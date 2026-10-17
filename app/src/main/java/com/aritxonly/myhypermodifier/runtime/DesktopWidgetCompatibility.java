package com.aritxonly.myhypermodifier;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** One-shot diagnostic request; never called from launcher draw/update hooks. */
final class DesktopWidgetCompatibility {
    private DesktopWidgetCompatibility() {}
    static Bundle read(Context context) throws Exception {
        int user = Process.myUid() / 100000;
        int launcherUid = context.getPackageManager().getPackageUid("com.miui.home", 0);
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress("mhm.widget.status." + user, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(2000);
            if (socket.getPeerCredentials().getUid() != launcherUid)
                throw new SecurityException("Unexpected diagnostic peer");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[256]; int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) {
                if (output.size() + count > 768) throw new IllegalStateException("Status too large");
                output.write(buffer, 0, count);
            }
            String status = output.toString(StandardCharsets.UTF_8.name());
            if (!status.startsWith("MHM_WIDGET_STATUS_V1\n")) throw new IllegalStateException("Status version mismatch");
            Bundle result = new Bundle(); result.putString("compatibility", status); return result;
        }
    }
}
