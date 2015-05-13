package mortenjust.com.trajectory2;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

public class WearMessageListenerService extends WearableListenerService {
    private static final String START_ACTIVITY = "/start_activity";
    private static final String TAG = "TRJ2Listnr";

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if( messageEvent.getPath().equalsIgnoreCase( START_ACTIVITY ) ) {
            Log.d(TAG, "Received Start in Service");
            Intent intent = new Intent( this, TrajectoryClock.class );
            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
            //startActivity( intent );
            startService(intent);
        } else {
            Log.d(TAG, "Received normal message in Service");
            super.onMessageReceived(messageEvent);
        }
    }
}