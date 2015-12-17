package tsinghua.mediatech.rafaelzig.pixelme;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.Map;

/**
 * Created by omar on 12/10/15.
 */
interface FeedAdapterListener{

    /**
     * It will fire an event after a Delete Button is tapped in any row, and will return the id of that element.
     *
     * @param entry_id
     */
    void didDeleteEntry(int entry_id);
}

public class FeedAdapter extends BaseAdapter {
    private ArrayList<FeedAdapterListener> listeners = new ArrayList<FeedAdapterListener>();
    Context context;
    ArrayList<Map<String, String>> data;
    private static LayoutInflater inflater = null;

    public FeedAdapter(Context context, ArrayList<Map<String, String>> data) {
        this.context = context;
        this.data = data;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    /**
     * For the didDeleteEntry(int id) interface method! and update on the SQLite Database
     *
     * @param listener just pass "this", but before implement this interface!
     */
    public void addFeedAdapterListener(FeedAdapterListener listener){
        listeners.add(listener);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, final View convertView, ViewGroup parent) {
        View vi = convertView;
        if(vi == null){
            vi = inflater.inflate(R.layout.row, null);
        }
        TextView text = (TextView)vi.findViewById(R.id.test_date);
        final VideoView videoView = (VideoView)vi.findViewById(R.id.video);
        Uri uri = Uri.parse(data.get(position).get("uri"));


        videoView.getLayoutParams().height = PixelMeStatics.getHeightForVideoView(context, uri);


        videoView.setVideoURI(uri);
        videoView.start();
        videoView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                VideoView vView = (VideoView)v;
                if(vView.isPlaying()){
                    vView.pause();
                }else{
                    vView.start();
                }
                return false;
            }
        });
        text.setText(data.get(position).get("created"));

        /* Button */

        Button deleteButton = (Button)vi.findViewById(R.id.delete_button);
        deleteButton.setTag(position);

        deleteButton.setOnClickListener(
                new Button.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Integer index = (Integer) v.getTag();
                        for(FeedAdapterListener listener : listeners){
                            listener.didDeleteEntry(Integer.parseInt(data.get(index.intValue()).get("id")));
                        }
                        data.remove(index.intValue());
                        notifyDataSetChanged();
                    }
                }
        );
        return vi;
    }
}
