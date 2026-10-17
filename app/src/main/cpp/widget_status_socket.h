#pragma once
#include "widget_status.h"
#include <cerrno>
#include <cstddef>
#include <poll.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

// Reuses the bounded install worker. No polling after installation settles:
// accept readiness blocks in poll until the settings page requests a snapshot.
// No commands or configuration are accepted over this diagnostic channel.
class WidgetStatusSocket {
    int fd = -1;
public:
    WidgetStatusSocket() {
        fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0);
        if (fd < 0) return;
        sockaddr_un address{}; address.sun_family = AF_UNIX;
        int n = std::snprintf(address.sun_path + 1, sizeof(address.sun_path) - 1,
            "mhm.widget.status.%u", getuid() / 100000);
        if (n <= 0 || static_cast<size_t>(n) >= sizeof(address.sun_path) - 1
                || bind(fd, reinterpret_cast<sockaddr *>(&address), offsetof(sockaddr_un, sun_path) + 1 + n)
                || listen(fd, 4)) { close(fd); fd = -1; }
    }
    ~WidgetStatusSocket() { if (fd >= 0) close(fd); }
    bool available() const { return fd >= 0; }
    void wait(int milliseconds, int wake = -1) const {
        pollfd events[]{{fd, POLLIN, 0}, {wake, POLLIN, 0}};
        if (poll(events, 2, milliseconds) <= 0) return;
        if (events[1].revents & POLLIN) { uint64_t value; (void)read(wake, &value, sizeof(value)); }
        if (!(events[0].revents & POLLIN)) return;
        // Bound work per wakeup, even if an unrelated app floods connections.
        for (int i = 0; i < 4; ++i) {
            int client = accept4(fd, nullptr, nullptr, SOCK_CLOEXEC | SOCK_NONBLOCK);
            if (client < 0) break;
            ucred peer{}; socklen_t length = sizeof(peer);
            if (!getsockopt(client, SOL_SOCKET, SO_PEERCRED, &peer, &length)
                    && peer.uid == 1000) {
                char response[768]; size_t size = widget_status::snapshot(response, sizeof(response));
                if (size < sizeof(response)) (void)send(client, response, size, MSG_DONTWAIT | MSG_NOSIGNAL);
            }
            close(client);
        }
    }
};
