package amidst.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.Iterator;

import amidst.Options;
import amidst.map.layer.BiomeLayer;
import amidst.map.object.MapObject;
import amidst.minecraft.Biome;

public class Map {
	public class Drawer {
		private AffineTransform mat = new AffineTransform();
		private Fragment currentFragment;

		public void draw(Graphics2D g, float time) {
			AffineTransform originalTransform = g.getTransform();
			drawLayer(originalTransform, createImageLayersDrawer(g, time));
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			fragmentManager.updateAllLayers(time);
			drawLayer(originalTransform, createLiveLayersDrawer(g, time));
			drawLayer(originalTransform, createObjectsDrawer(g));
			g.setTransform(originalTransform);
		}

		private void drawLayer(AffineTransform originalTransform,
				Runnable theDrawer) {
			initMat(originalTransform, zoom.getCurrentValue());
			for (Fragment fragment : iterable) {
				currentFragment = fragment;
				theDrawer.run();
				mat.translate(Fragment.SIZE, 0);
				if (currentFragment.isEndOfLine()) {
					mat.translate(-Fragment.SIZE * fragmentsPerRow,
							Fragment.SIZE);
				}
			}
		}

		private Runnable createImageLayersDrawer(final Graphics2D g,
				final float time) {
			return new Runnable() {
				@Override
				public void run() {
					currentFragment.drawImageLayers(time, g, mat);
				}
			};
		}

		private Runnable createLiveLayersDrawer(final Graphics2D g,
				final float time) {
			return new Runnable() {
				@Override
				public void run() {
					currentFragment.drawLiveLayers(time, g, mat);
				}
			};
		}

		private Runnable createObjectsDrawer(final Graphics2D g) {
			return new Runnable() {
				@Override
				public void run() {
					currentFragment.drawObjects(g, mat, Map.this);
				}
			};
		}

		private void initMat(AffineTransform originalTransform, double scale) {
			mat.setToIdentity();
			mat.concatenate(originalTransform);
			mat.translate(start.x, start.y);
			mat.scale(scale, scale);
		}
	}

	private static class MapIterable implements Iterable<Fragment> {
		private Map map;

		public MapIterable(Map map) {
			this.map = map;
		}

		@Override
		public Iterator<Fragment> iterator() {
			return new MapIterator(map);
		}
	}

	private static class MapIterator implements Iterator<Fragment> {
		private Fragment currentNode;

		public MapIterator(Map map) {
			currentNode = map.startNode.getNext();
		}

		@Override
		public boolean hasNext() {
			return currentNode != null;
		}

		@Override
		public Fragment next() {
			Fragment result = currentNode;
			currentNode = currentNode.getNext();
			return result;
		}
	}

	private MapIterable iterable = new MapIterable(this);

	private Drawer drawer = new Drawer();

	private MapObject selectedMapObject;

	private static final boolean START = true;
	private static final boolean END = false;
	private FragmentManager fragmentManager;

	private Fragment startNode = new Fragment();

	private Point2D.Double start = new Point2D.Double();

	private int fragmentsPerRow;
	private int fragmentsPerColumn;
	private int viewerWidth = 1;
	private int viewerHeight = 1;

	private final Object mapLock = new Object();

	private MapZoom zoom;

	public Map(FragmentManager fragmentManager, MapZoom zoom) {
		this.fragmentManager = fragmentManager;
		this.fragmentManager.setMap(this);
		this.zoom = zoom;
		safeAddStart(0, 0);
	}

	private void lockedDraw(Graphics2D g, float time) {
		int scaledFragmentSize = (int) (Fragment.SIZE * zoom.getCurrentValue());
		int desiredFragmentsPerRow = viewerWidth / scaledFragmentSize + 2;
		int desiredFragmentsPerColumn = viewerHeight / scaledFragmentSize + 2;
		lockedAdjustNumberOfRowsAndColumns(desiredFragmentsPerRow,
				desiredFragmentsPerColumn);
		lockedMoveStart(scaledFragmentSize);
		drawer.draw(g, time);
	}

	private void lockedAdjustNumberOfRowsAndColumns(int desiredFragmentsPerRow,
			int desiredFragmentsPerColumn) {
		while (fragmentsPerRow < desiredFragmentsPerRow) {
			lockedAddColumn(END);
		}
		while (fragmentsPerRow > desiredFragmentsPerRow) {
			lockedRemoveColumn(END);
		}
		while (fragmentsPerColumn < desiredFragmentsPerColumn) {
			lockedAddRow(END);
		}
		while (fragmentsPerColumn > desiredFragmentsPerColumn) {
			lockedRemoveRow(END);
		}
	}

	private void lockedMoveStart(int size) {
		while (start.x > 0) {
			start.x -= size;
			lockedAddColumn(START);
			lockedRemoveColumn(END);
		}
		while (start.x < -size) {
			start.x += size;
			lockedAddColumn(END);
			lockedRemoveColumn(START);
		}
		while (start.y > 0) {
			start.y -= size;
			lockedAddRow(START);
			lockedRemoveRow(END);
		}
		while (start.y < -size) {
			start.y += size;
			lockedAddRow(END);
			lockedRemoveRow(START);
		}
	}

	private void lockedAddStart(int x, int y) {
		Fragment start = fragmentManager.requestFragment(x, y);
		start.setEndOfLine(true);
		lockedSetFirstFragment(start);
		fragmentsPerRow = 1;
		fragmentsPerColumn = 1;
	}

	private void lockedAddRow(boolean start) {
		Fragment fragment = startNode;
		int y;
		if (start) {
			fragment = getFirstFragment();
			y = fragment.getYInWorld() - Fragment.SIZE;
		} else {
			fragment = lockedGetLastFragment();
			y = fragment.getYInWorld() + Fragment.SIZE;
		}

		fragmentsPerColumn++;
		Fragment newFragment = fragmentManager.requestFragment(
				getFirstFragment().getXInWorld(), y);
		Fragment chainFragment = newFragment;
		for (int i = 1; i < fragmentsPerRow; i++) {
			Fragment tempFragment = fragmentManager.requestFragment(
					chainFragment.getXInWorld() + Fragment.SIZE,
					chainFragment.getYInWorld());
			chainFragment.setNext(tempFragment);
			chainFragment = tempFragment;
			if (i == (fragmentsPerRow - 1)) {
				chainFragment.setEndOfLine(true);
			}
		}
		if (start) {
			chainFragment.setNext(fragment);
			lockedSetFirstFragment(newFragment);
		} else {
			fragment.setNext(newFragment);
		}
	}

	private void lockedAddColumn(boolean start) {
		int x = 0;
		if (start) {
			x = startNode.getNext().getXInWorld() - Fragment.SIZE;
			Fragment newFragment = fragmentManager.requestFragment(x, startNode
					.getNext().getYInWorld());
			newFragment.setNext(getFirstFragment());
			lockedSetFirstFragment(newFragment);
		}
		Fragment fragment = startNode;
		while (fragment.hasNext()) {
			fragment = fragment.getNext();
			if (fragment.isEndOfLine()) {
				if (start) {
					if (fragment.hasNext()) {
						Fragment newFragment = fragmentManager.requestFragment(
								x, fragment.getYInWorld() + Fragment.SIZE);
						newFragment.setNext(fragment.getNext());
						fragment.setNext(newFragment);
						fragment = newFragment;
					}
				} else {
					Fragment newFragment = fragmentManager.requestFragment(
							fragment.getXInWorld() + Fragment.SIZE,
							fragment.getYInWorld());

					if (fragment.hasNext()) {
						newFragment.setNext(fragment.getNext());
					}
					newFragment.setEndOfLine(true);
					fragment.setEndOfLine(false);
					fragment.setNext(newFragment);
					fragment = newFragment;
				}
			}
		}
		fragmentsPerRow++;
	}

	private void lockedRemoveRow(boolean start) {
		if (start) {
			for (int i = 0; i < fragmentsPerRow; i++) {
				Fragment fragment = getFirstFragment();
				fragment.remove();
				fragmentManager.recycleFragment(fragment);
			}
		} else {
			Fragment fragment = lockedGetLastFragment();
			for (int i = 0; i < fragmentsPerRow; i++) {
				fragment.remove();
				fragmentManager.recycleFragment(fragment);
				fragment = fragment.getPrevious();
			}
		}
		fragmentsPerColumn--;
	}

	private void lockedRemoveColumn(boolean start) {
		if (start) {
			fragmentManager.recycleFragment(getFirstFragment());
			getFirstFragment().remove();
		}
		Fragment fragment = startNode;
		while (fragment.hasNext()) {
			fragment = fragment.getNext();
			if (fragment.isEndOfLine()) {
				if (start) {
					if (fragment.hasNext()) {
						Fragment tempFragment = fragment.getNext();
						tempFragment.remove();
						fragmentManager.recycleFragment(tempFragment);
					}
				} else {
					fragment.getPrevious().setEndOfLine(true);
					fragment.remove();
					fragmentManager.recycleFragment(fragment);
					fragment = fragment.getPrevious();
				}
			}
		}
		fragmentsPerRow--;
	}

	private void lockedCenterOn(long x, long y) {
		long fragOffsetX = x % Fragment.SIZE;
		long fragOffsetY = y % Fragment.SIZE;
		long startX = x - fragOffsetX;
		long startY = y - fragOffsetY;
		while (fragmentsPerColumn > 1) {
			lockedRemoveRow(false);
		}
		while (fragmentsPerRow > 1) {
			lockedRemoveColumn(false);
		}
		Fragment fragment = getFirstFragment();
		fragment.remove();
		fragmentManager.recycleFragment(fragment);
		// TODO: Support longs?
		double offsetX = viewerWidth >> 1;
		double offsetY = viewerHeight >> 1;

		offsetX -= (fragOffsetX) * zoom.getCurrentValue();
		offsetY -= (fragOffsetY) * zoom.getCurrentValue();

		start.x = offsetX;
		start.y = offsetY;

		lockedAddStart((int) startX, (int) startY);
	}

	private Fragment lockedGetLastFragment() {
		Fragment result = null;
		for (Fragment fragment : iterable) {
			result = fragment;
		}
		return result;
	}

	private void lockedSetFirstFragment(Fragment start) {
		startNode.setNext(start);
	}

	private void safeAddStart(int startX, int startY) {
		synchronized (mapLock) {
			lockedAddStart(startX, startY);
		}
	}

	public void safeDraw(Graphics2D g, float time) {
		synchronized (mapLock) {
			lockedDraw(g, time);
		}
	}

	public void safeCenterOn(long x, long y) {
		synchronized (mapLock) {
			lockedCenterOn(x, y);
		}
	}

	public void safeDispose() {
		synchronized (mapLock) {
			lockedDispose();
		}
	}

	private Fragment getFirstFragment() {
		return startNode.getNext();
	}

	public Fragment getFragmentAt(Point position) {
		Point cornerPosition = new Point(position.x >> Fragment.SIZE_SHIFT,
				position.y >> Fragment.SIZE_SHIFT);
		Point fragmentPosition = new Point();
		for (Fragment fragment : iterable) {
			fragmentPosition.x = fragment.getFragmentXInWorld();
			fragmentPosition.y = fragment.getFragmentYInWorld();
			if (cornerPosition.equals(fragmentPosition)) {
				return fragment;
			}
		}
		return null;
	}

	public MapObject getObjectAt(Point position, double maxRange) {
		double x = start.x;
		double y = start.y;
		MapObject closestObject = null;
		double closestDistance = maxRange;
		int size = (int) (Fragment.SIZE * zoom.getCurrentValue());
		for (Fragment fragment : iterable) {
			for (MapObject mapObject : fragment.getMapObjects()) {
				if (mapObject.isVisible()) {
					double distance = getPosition(x, y, mapObject).distance(
							position);
					if (closestDistance > distance) {
						closestDistance = distance;
						closestObject = mapObject;
					}
				}
			}
			x += size;
			if (fragment.isEndOfLine()) {
				x = start.x;
				y += size;
			}
		}
		return closestObject;
	}

	private Point getPosition(double x, double y, MapObject mapObject) {
		Point result = new Point(mapObject.getXInFragment(),
				mapObject.getYInFragment());
		result.x *= zoom.getCurrentValue();
		result.y *= zoom.getCurrentValue();
		result.x += x;
		result.y += y;
		return result;
	}

	public String getBiomeNameAt(Point point) {
		for (Fragment fragment : iterable) {
			if ((fragment.getXInWorld() <= point.x)
					&& (fragment.getYInWorld() <= point.y)
					&& (fragment.getXInWorld() + Fragment.SIZE > point.x)
					&& (fragment.getYInWorld() + Fragment.SIZE > point.y)) {
				int x = point.x - fragment.getXInWorld();
				int y = point.y - fragment.getYInWorld();

				return getBiomeNameForFragment(fragment, x, y);
			}
		}
		return "Unknown";
	}

	public String getBiomeAliasAt(Point point) {
		for (Fragment fragment : iterable) {
			if ((fragment.getXInWorld() <= point.x)
					&& (fragment.getYInWorld() <= point.y)
					&& (fragment.getXInWorld() + Fragment.SIZE > point.x)
					&& (fragment.getYInWorld() + Fragment.SIZE > point.y)) {
				int x = point.x - fragment.getXInWorld();
				int y = point.y - fragment.getYInWorld();

				return getBiomeAliasForFragment(fragment, x, y);
			}
		}
		return "Unknown";
	}

	private String getBiomeNameForFragment(Fragment fragment, int blockX,
			int blockY) {
		return Biome.biomes[getBiomeForFragment(fragment, blockX, blockY)].name;
	}

	private String getBiomeAliasForFragment(Fragment fragment, int blockX,
			int blockY) {
		return Options.instance.biomeColorProfile
				.getAliasForId(getBiomeForFragment(fragment, blockX, blockY));
	}

	private int getBiomeForFragment(Fragment fragment, int blockX, int blockY) {
		int index = (blockY >> 2) * Fragment.BIOME_SIZE + (blockX >> 2);
		return fragment.getBiomeData()[index];
	}

	public void moveBy(Point2D.Double speed) {
		moveBy(speed.x, speed.y);
	}

	public void moveBy(double x, double y) {
		start.x += x;
		start.y += y;
	}

	public Point screenToLocal(Point inPoint) {
		Point point = inPoint.getLocation();

		point.x -= start.x;
		point.y -= start.y;

		// TODO: int -> double -> int = bad?
		point.x /= zoom.getCurrentValue();
		point.y /= zoom.getCurrentValue();

		point.x += getFirstFragment().getXInWorld();
		point.y += getFirstFragment().getYInWorld();

		return point;
	}

	public Point2D.Double getScaled(double oldScale, double newScale, Point p) {
		double baseX = p.x - start.x;
		double scaledX = baseX - (baseX / oldScale) * newScale;

		double baseY = p.y - start.y;
		double scaledY = baseY - (baseY / oldScale) * newScale;

		return new Point2D.Double(scaledX, scaledY);
	}

	private void repaintImageLayer(int id) {
		for (Fragment fragment : iterable) {
			fragmentManager.repaintFragmentImageLayer(fragment, id);
		}
	}

	private void lockedDispose() {
		fragmentManager.reset();
	}

	public double getZoom() {
		return zoom.getCurrentValue();
	}

	public int getFragmentsPerRow() {
		return fragmentsPerRow;
	}

	public int getFragmentsPerColumn() {
		return fragmentsPerColumn;
	}

	public void setViewerWidth(int viewerWidth) {
		this.viewerWidth = viewerWidth;
	}

	public void setViewerHeight(int viewerHeight) {
		this.viewerHeight = viewerHeight;
	}

	public MapObject getSelectedMapObject() {
		return selectedMapObject;
	}

	public void setSelectedMapObject(MapObject selectedMapObject) {
		this.selectedMapObject = selectedMapObject;
	}

	public FragmentManager getFragmentManager() {
		return fragmentManager;
	}

	// TODO: move the thread somewhere else?
	@Deprecated
	public void repaintBiomeLayer() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				repaintImageLayer(BiomeLayer.getInstance().getLayerId());
			}
		}).start();
	}
}
