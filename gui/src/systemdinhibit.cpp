#include "systemdinhibit.h"

#ifdef CHIAKI_HAVE_DBUS
#include <fcntl.h>
#include <unistd.h>
#include <QDBusMessage>
#include <QDBusConnection>
#include <QDBusObjectPath>
#include <QDBusPendingReply>
#include <QDBusPendingCallWatcher>
#include <QDBusUnixFileDescriptor>
#endif

SystemdInhibit::SystemdInhibit(const QString &who, const QString &why, const QString &what, const QString &mode, QObject *parent)
    : QObject(parent)
    , who(who)
    , why(why)
    , what(what)
    , mode(mode)
{
#ifdef CHIAKI_HAVE_DBUS
    QDBusConnection::systemBus().connect(QStringLiteral("org.freedesktop.login1"),
                                         QStringLiteral("/org/freedesktop/login1"),
                                         QStringLiteral("org.freedesktop.login1.Manager"),
                                         QStringLiteral("PrepareForSleep"),
                                         this,
                                         SIGNAL(login1PrepareForSleep(bool)));
#endif
}

void SystemdInhibit::inhibit()
{
#ifdef CHIAKI_HAVE_DBUS
    QDBusMessage call = QDBusMessage::createMethodCall(QStringLiteral("org.freedesktop.login1"),
                                                       QStringLiteral("/org/freedesktop/login1"),
                                                       QStringLiteral("org.freedesktop.login1.Manager"),
                                                       QStringLiteral("Inhibit"));
    call << what << who << why << mode;

    QDBusPendingCallWatcher *watcher = new QDBusPendingCallWatcher(QDBusConnection::systemBus().asyncCall(call), this);
    connect(watcher, &QDBusPendingCallWatcher::finished, this, [this, watcher]() {
        watcher->deleteLater();
        const QDBusPendingReply<QDBusUnixFileDescriptor> reply = *watcher;
        if (reply.isError()) {
            qWarning() << "Inhibit Error:" << reply.error().name() << reply.error().message();
        } else {
            fd = reply.value().fileDescriptor();
            if (fd == -1)
                qWarning() << "Received invalid fd";
            else
                fd = fcntl(fd, F_DUPFD_CLOEXEC, 3);
        }
    });

    QDBusMessage portalCall = QDBusMessage::createMethodCall(QStringLiteral("org.freedesktop.portal.Desktop"),
                                                             QStringLiteral("/org/freedesktop/portal/desktop"),
                                                             QStringLiteral("org.freedesktop.portal.Inhibit"),
                                                             QStringLiteral("Inhibit"));
    portalCall << QString()
               << quint32(8)
               << QVariantMap{{QStringLiteral("reason"), QVariant(QStringLiteral("Streaming session active"))}};

    QDBusPendingCallWatcher *portalWatcher = new QDBusPendingCallWatcher(QDBusConnection::sessionBus().asyncCall(portalCall), this);
    connect(portalWatcher, &QDBusPendingCallWatcher::finished, this, [this, portalWatcher]() {
        portalWatcher->deleteLater();
        const QDBusPendingReply<QDBusObjectPath> reply = *portalWatcher;
        if (reply.isError())
            qWarning() << "Portal Inhibit Error:" << reply.error().name() << reply.error().message();
        else
            idleInhibitPath = reply.value().path();
    });
#endif
}


void SystemdInhibit::login1PrepareForSleep(bool start)
{
    if (start)
        emit sleep();
    else
        emit resume();
}

void SystemdInhibit::release()
{
#ifdef CHIAKI_HAVE_DBUS
    if (fd >= 0)
        close(fd);
    fd = -1;

    if (!idleInhibitPath.isEmpty()) {
        QDBusMessage closeCall = QDBusMessage::createMethodCall(QStringLiteral("org.freedesktop.portal.Desktop"),
                                                                idleInhibitPath,
                                                                QStringLiteral("org.freedesktop.portal.Request"),
                                                                QStringLiteral("Close"));
        QDBusConnection::sessionBus().asyncCall(closeCall);
        idleInhibitPath.clear();
    }
#endif
}
