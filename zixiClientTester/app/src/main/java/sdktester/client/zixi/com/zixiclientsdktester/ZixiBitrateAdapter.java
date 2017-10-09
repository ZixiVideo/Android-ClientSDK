package sdktester.client.zixi.com.zixiclientsdktester;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * Created by roy on 9/18/2017.
 */

public class ZixiBitrateAdapter  extends ArrayAdapter<String> {
    private int mActiveId;
    public ZixiBitrateAdapter(@NonNull Context context){
        super(context, android.R.layout.simple_list_item_1);
        mActiveId = -1;
    }

    public void setActiveId(int id) {
        mActiveId = id;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView text = (TextView) view.findViewById(android.R.id.text1);
        if (position == mActiveId) {
            text.setTextColor(Color.RED);
            text.setShadowLayer(1.6f,1.5f,1.3f, Color.BLACK);
        } else {
            text.setTextColor(Color.YELLOW);
            text.setShadowLayer(1.6f,1.5f,1.3f, Color.BLACK);
        }
        return view;
    }
    @Override
    public boolean isEnabled(int position) {
        return false;
    }
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }
}
