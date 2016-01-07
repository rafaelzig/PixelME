package tsinghua.mediatech.rafaelzig.pixelme.camera.component;

import android.support.v7.widget.RecyclerView;
import android.view.*;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import tsinghua.mediatech.rafaelzig.pixelme.R;

import java.io.File;
import java.util.LinkedList;

/**
 * Created by Zig on 19/12/2015.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder>
{
	private final LinkedList<String>        imagePaths;
	private final RecyclerViewClickObserver observer;

	public ImageAdapter(File[] galleryFolder, RecyclerViewClickObserver observer)
	{
		imagePaths = new LinkedList<>();

		for (File file : galleryFolder)
		{
			imagePaths.addLast(file.getAbsolutePath());
		}

		this.observer = observer;
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
		Glide.with(imageView.getContext()).load(imagePaths.get(position)).into(imageView);
	}

	@Override
	public int getItemCount()
	{
		return imagePaths.size();
	}

	public void addImage(String imagePath)
	{
		imagePaths.addLast(imagePath);
		notifyItemInserted(getItemCount() - 1);
	}

	public boolean removeImage(int position)
	{
		File image = new File(imagePaths.get(position));

		if (image.delete())
		{
			imagePaths.remove(position);
			notifyItemRemoved(position);

			return true;
		}

		return false;
	}

	class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
	{
		private ImageView imageView;

		ViewHolder(View view)
		{
			super(view);

			view.setOnClickListener(this);
			imageView = (ImageView) view.findViewById(R.id.imageGalleryView);
		}

		ImageView getImageView()
		{
			return imageView;
		}

		@Override
		public void onClick(View v)
		{
			observer.notifyImageHolderClicked(imagePaths.get(getAdapterPosition()));
		}
	}
}