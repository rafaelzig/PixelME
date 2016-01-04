package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import tsinghua.mediatech.rafaelzig.pixelme.R;

import java.io.File;

/**
 * Created by Zig on 19/12/2015.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>
{
	private File[]                    imageFiles;
	private RecyclerViewClickPosition positionInterface;

	ImageAdapter(File[] imageFiles)
	{
		this.imageFiles = imageFiles;
	}

	public ImageAdapter(File[] imageFiles, RecyclerViewClickPosition positionInterface)
	{
		this(imageFiles);
		this.positionInterface = positionInterface;
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType)
	{
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.gallery_images, parent, false));
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position)
	{
		ImageView imageView = holder.getImageView();
		File imageFile = imageFiles[position];
		Glide.with(imageView.getContext()).load(imageFile).into(imageView);
	}

	@Override
	public int getItemCount()
	{
		return imageFiles.length;
	}

	class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener
	{
		private ImageView imageView;

		ViewHolder(View view)
		{
			super(view);

			view.setOnClickListener(this);
			view.setOnLongClickListener(this);
			imageView = (ImageView) view.findViewById(R.id.imageGalleryView);
		}

		ImageView getImageView()
		{
			return imageView;
		}

		@Override
		public void onClick(View v)
		{
			positionInterface.getRecyclerViewAdapterPosition(getAdapterPosition());
		}

		@Override
		public boolean onLongClick(View v)
		{
			int position = getAdapterPosition();

			if (imageFiles[position].delete())
			{
				notifyItemRemoved(position);
				notifyItemRangeChanged(position, imageFiles.length);
				return true;
			}

			return false;
		}
	}
}