// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

#include "video-decoder.h"

#include <jni.h>

#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window_jni.h>

#include <string.h>
#include <time.h>

#define INPUT_BUFFER_TIMEOUT_MS 10

static void *android_chiaki_video_decoder_output_thread_func(void *user);

ChiakiErrorCode android_chiaki_video_decoder_init(AndroidChiakiVideoDecoder *decoder, ChiakiLog *log, int32_t target_width, int32_t target_height, ChiakiCodec codec)
{
	decoder->log = log;
	decoder->codec = NULL;
	decoder->timestamp_cur = 0;
	decoder->target_width = target_width;
	decoder->target_height = target_height;
	decoder->target_codec = codec;
	decoder->shutdown_output = false;
	decoder->fps_frames = 0;
	decoder->fps_last_time_ns = 0;
	decoder->current_fps = 0.0;
	decoder->cumulative_drops = 0;
	decoder->ema_decode_time_ms = 0.0;
	memset(decoder->submit_times, 0, sizeof(decoder->submit_times));
	return chiaki_mutex_init(&decoder->codec_mutex, false);
}

static void kill_decoder(AndroidChiakiVideoDecoder *decoder)
{
	chiaki_mutex_lock(&decoder->codec_mutex);
	decoder->shutdown_output = true;
	ssize_t codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, 1000);
	if(codec_buf_index >= 0)
	{
		CHIAKI_LOGI(decoder->log, "Video Decoder sending EOS buffer");
		AMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, 0, decoder->timestamp_cur++, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
		AMediaCodec_stop(decoder->codec);
		chiaki_mutex_unlock(&decoder->codec_mutex);
		chiaki_thread_join(&decoder->output_thread, NULL);
	}
	else
	{
		CHIAKI_LOGE(decoder->log, "Failed to get input buffer for shutting down Video Decoder!");
		AMediaCodec_stop(decoder->codec);
		chiaki_mutex_unlock(&decoder->codec_mutex);
	}
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;
	decoder->shutdown_output = false;
}

void android_chiaki_video_decoder_fini(AndroidChiakiVideoDecoder *decoder)
{
	if(decoder->codec)
		kill_decoder(decoder);
	chiaki_mutex_fini(&decoder->codec_mutex);
}

void android_chiaki_video_decoder_set_surface(AndroidChiakiVideoDecoder *decoder, JNIEnv *env, jobject surface)
{
	chiaki_mutex_lock(&decoder->codec_mutex);

	if(!surface)
	{
		if(decoder->codec)
		{
			kill_decoder(decoder);
			CHIAKI_LOGI(decoder->log, "Decoder shut down after surface was removed");
		}
		return;
	}

	if(decoder->codec)
	{
#if __ANDROID_API__ >= 23
		CHIAKI_LOGI(decoder->log, "Video decoder already initialized, swapping surface");
		ANativeWindow *new_window = surface ? ANativeWindow_fromSurface(env, surface) : NULL;
		AMediaCodec_setOutputSurface(decoder->codec, new_window);
		ANativeWindow_release(decoder->window);
		decoder->window = new_window;
#else
		CHIAKI_LOGE(decoder->log, "Video Decoder already initialized");
#endif
		goto beach;
	}

	decoder->window = ANativeWindow_fromSurface(env, surface);

	const char *mime = chiaki_codec_is_h265(decoder->target_codec) ? "video/hevc" : "video/avc";
	CHIAKI_LOGI(decoder->log, "Initializing decoder with mime %s", mime);

	decoder->codec = AMediaCodec_createDecoderByType(mime);
	if(!decoder->codec)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create AMediaCodec for mime type %s", mime);
		goto error_surface;
	}

	AMediaFormat *format = AMediaFormat_new();
	AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, decoder->target_width);
	AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, decoder->target_height);

	media_status_t r = AMediaCodec_configure(decoder->codec, format, decoder->window, NULL, 0);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_configure() failed: %d", (int)r);
		AMediaFormat_delete(format);
		goto error_codec;
	}

	r = AMediaCodec_start(decoder->codec);
	AMediaFormat_delete(format);
	if(r != AMEDIA_OK)
	{
		CHIAKI_LOGE(decoder->log, "AMediaCodec_start() failed: %d", (int)r);
		goto error_codec;
	}

	ChiakiErrorCode err = chiaki_thread_create(&decoder->output_thread, android_chiaki_video_decoder_output_thread_func, decoder);
	if(err != CHIAKI_ERR_SUCCESS)
	{
		CHIAKI_LOGE(decoder->log, "Failed to create output thread for AMediaCodec");
		goto error_codec;
	}

	goto beach;

error_codec:
	AMediaCodec_delete(decoder->codec);
	decoder->codec = NULL;

error_surface:
	ANativeWindow_release(decoder->window);
	decoder->window = NULL;

beach:
	chiaki_mutex_unlock(&decoder->codec_mutex);
}

bool android_chiaki_video_decoder_video_sample(uint8_t *buf, size_t buf_size, int32_t frames_lost, bool frame_recovered, void *user)
{
	bool r = true;
	AndroidChiakiVideoDecoder *decoder = user;

	if(frames_lost > 0)
		decoder->cumulative_drops += (int64_t)frames_lost;

	chiaki_mutex_lock(&decoder->codec_mutex);

	if(!decoder->codec)
	{
		CHIAKI_LOGE(decoder->log, "Received video data, but decoder is not initialized!");
		chiaki_mutex_unlock(&decoder->codec_mutex);
		return false;
	}

	while(buf_size > 0)
	{
		ssize_t codec_buf_index = -1;
		for(int attempt = 0; attempt < 3; attempt++)
		{
			codec_buf_index = AMediaCodec_dequeueInputBuffer(decoder->codec, INPUT_BUFFER_TIMEOUT_MS * 1000);
			if(codec_buf_index >= 0)
				break;
		}
		if(codec_buf_index < 0)
		{
			CHIAKI_LOGE(decoder->log, "Failed to get input buffer");
			r = false;
			break;
		}

		size_t codec_buf_size;
		uint8_t *codec_buf = AMediaCodec_getInputBuffer(decoder->codec, (size_t)codec_buf_index, &codec_buf_size);
		size_t codec_sample_size = buf_size;
		if(codec_sample_size > codec_buf_size)
			codec_sample_size = codec_buf_size;
		memcpy(codec_buf, buf, codec_sample_size);

		uint64_t pts = decoder->timestamp_cur++;
		struct timespec ts;
		clock_gettime(CLOCK_MONOTONIC, &ts);
		decoder->submit_times[pts & (DECODER_SUBMIT_RING_SIZE - 1)] = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;

		media_status_t status = AMediaCodec_queueInputBuffer(decoder->codec, (size_t)codec_buf_index, 0, codec_sample_size, pts, 0);
		if(status != AMEDIA_OK)
		{
			CHIAKI_LOGE(decoder->log, "AMediaCodec_queueInputBuffer() failed: %d", (int)status);
		}
		buf += codec_sample_size;
		buf_size -= codec_sample_size;
	}

	chiaki_mutex_unlock(&decoder->codec_mutex);
	return r;
}

static void *android_chiaki_video_decoder_output_thread_func(void *user)
{
	AndroidChiakiVideoDecoder *decoder = user;

	while(1)
	{
		AMediaCodecBufferInfo info;
		ssize_t status = AMediaCodec_dequeueOutputBuffer(decoder->codec, &info, -1);
		if(status >= 0)
		{
			if(info.size != 0)
			{
				AMediaCodec_releaseOutputBuffer(decoder->codec, (size_t)status, true);

				chiaki_mutex_lock(&decoder->codec_mutex);
				decoder->fps_frames++;
				struct timespec ts;
				clock_gettime(CLOCK_MONOTONIC, &ts);
				int64_t now_ns = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
				int64_t submit_ns = decoder->submit_times[info.presentationTimeUs & (DECODER_SUBMIT_RING_SIZE - 1)];
				if(submit_ns > 0)
				{
					double decode_time_ns = (double)(now_ns - submit_ns);
					if(decode_time_ns > 0 && decode_time_ns < 100000000LL)
					{
						double decode_time_ms = decode_time_ns / 1000000.0;
						if(decoder->ema_decode_time_ms == 0.0)
							decoder->ema_decode_time_ms = decode_time_ms;
						else
							decoder->ema_decode_time_ms = decoder->ema_decode_time_ms * 0.9 + decode_time_ms * 0.1;
					}
				}
				if(decoder->fps_last_time_ns == 0)
					decoder->fps_last_time_ns = now_ns;
				int64_t elapsed_ns = now_ns - decoder->fps_last_time_ns;
				if(elapsed_ns >= 1000000000LL)
				{
					decoder->current_fps = (double)decoder->fps_frames * 1000000000.0 / (double)elapsed_ns;
					decoder->fps_frames = 0;
					decoder->fps_last_time_ns = now_ns;
				}
				chiaki_mutex_unlock(&decoder->codec_mutex);
			}
			else
			{
				AMediaCodec_releaseOutputBuffer(decoder->codec, (size_t)status, false);
			}

			if(info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM)
			{
				CHIAKI_LOGI(decoder->log, "AMediaCodec reported EOS");
				break;
			}
		}
		else
		{
			chiaki_mutex_lock(&decoder->codec_mutex);
			bool shutdown = decoder->shutdown_output;
			chiaki_mutex_unlock(&decoder->codec_mutex);
			if(shutdown)
			{
				CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread detected shutdown after reported error");
				break;
			}
		}
	}

	CHIAKI_LOGI(decoder->log, "Video Decoder Output Thread exiting");

	return NULL;
}

double android_chiaki_video_decoder_get_fps(AndroidChiakiVideoDecoder *decoder)
{
	double fps;
	chiaki_mutex_lock(&decoder->codec_mutex);
	fps = decoder->current_fps;
	chiaki_mutex_unlock(&decoder->codec_mutex);
	return fps;
}

int64_t android_chiaki_video_decoder_get_cumulative_drops(AndroidChiakiVideoDecoder *decoder)
{
	int64_t drops;
	chiaki_mutex_lock(&decoder->codec_mutex);
	drops = decoder->cumulative_drops;
	chiaki_mutex_unlock(&decoder->codec_mutex);
	return drops;
}

double android_chiaki_video_decoder_get_avg_decode_time_ms(AndroidChiakiVideoDecoder *decoder)
{
	double dt;
	chiaki_mutex_lock(&decoder->codec_mutex);
	dt = decoder->ema_decode_time_ms;
	chiaki_mutex_unlock(&decoder->codec_mutex);
	return dt;
}
