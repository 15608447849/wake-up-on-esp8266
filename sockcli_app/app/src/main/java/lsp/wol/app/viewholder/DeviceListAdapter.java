package lsp.wol.app.viewholder;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;
import lsp.wol.app.model.DeviceEventCallback;

public class DeviceListAdapter  extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final AsyncListDiffer<Device> listDiffer = new AsyncListDiffer<>(this, new DiffUtil.ItemCallback<>(){
        @Override
        public boolean areItemsTheSame(@NonNull Device oldItem, @NonNull Device newItem) {
            // 是否是「同一个对象」
            return oldItem.macAddress.equals(newItem.macAddress);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Device oldItem, @NonNull Device newItem) {
            // 所有属性都相同，才认为内容没变化
            return oldItem.name.equals(newItem.name)
                    && oldItem.macAddress.equals(oldItem.macAddress);
        }
    });

    private Context mContext;
    private DeviceEventCallback callback;

    public DeviceListAdapter(Context context, DeviceEventCallback callback) {
        this.mContext = context;
        this.callback = callback;
    }

    // 绑定数据
    public void addDevices(List<Device> list){
        Log.i(String.valueOf(R.string.app_name), "addDevices: 添加数据条数 "+ list.size());

        listDiffer.submitList(list);
    }

    // 创建 ViewHolder
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(mContext)
                .inflate(R.layout.device_list_item, parent, false);
        // 创建 ViewHolder 并返回
        return new DeviceItemViewHolder(itemView,callback);

    }
    // 绑定数据到 ViewHolder
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        DeviceItemViewHolder deviceItemViewHolder = (DeviceItemViewHolder) holder;
        Device device = listDiffer.getCurrentList().get(position);
        deviceItemViewHolder.buildDevice(device);
    }

    @Override
    public int getItemCount() {
        return listDiffer.getCurrentList().size();
    }
}
