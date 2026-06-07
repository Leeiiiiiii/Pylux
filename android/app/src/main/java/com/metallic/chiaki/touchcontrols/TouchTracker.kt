// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.metallic.chiaki.touchcontrols

import android.view.MotionEvent

class TouchTracker
{
	var currentPosition: Vector? = null
		private set(value)
		{
			field = value
			positionChangedCallback?.let { it(field) }
		}

	var positionChangedCallback: ((Vector?) -> Unit)? = null

	private var pointerId: Int? = null

	fun touchEvent(event: MotionEvent)
	{
		when(event.actionMasked)
		{
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
			{
				if(pointerId == null)
				{
					pointerId = event.getPointerId(event.actionIndex)
					currentPosition = Vector(event.getX(event.actionIndex), event.getY(event.actionIndex))
				}
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
			{
				if(pointerId != null && event.getPointerId(event.actionIndex) == pointerId)
				{
					pointerId = null
					currentPosition = null
				}
			}

			MotionEvent.ACTION_CANCEL ->
			{
				pointerId = null
				currentPosition = null
			}

			MotionEvent.ACTION_MOVE ->
			{
				val activeId = pointerId ?: return
				val pointerIndex = event.findPointerIndex(activeId)
				if(pointerIndex < 0)
				{
					pointerId = null
					currentPosition = null
					return
				}
				currentPosition = Vector(event.getX(pointerIndex), event.getY(pointerIndex))
			}
		}
	}
}