package pl.szczodrzynski.edziennik.core.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import pl.szczodrzynski.edziennik.ui.widgets.WidgetUpdater;

public class UserPresentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            WidgetUpdater.INSTANCE.updateAll(context);
        }
    }
}
