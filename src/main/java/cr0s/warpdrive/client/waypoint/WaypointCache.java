package cr0s.warpdrive.client.waypoint;

import cr0s.warpdrive.client.waypoint.WaypointDiscovery.MapWaypoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Client-side cache over {@link WaypointDiscovery}: the reflection scan of installed map mods is
 * expensive enough that repeated GUI refreshes should not re-run it, and probe results from the
 * server need a stable home shared by the waypoint list and the surface map pins.
 */
public final class WaypointCache {

	private static final long SCAN_TTL_MS = 5_000L;

	private static final ArrayList<MapWaypoint> cachedWaypoints = new ArrayList<>();
	private static final HashMap<String, WaypointStatus> statusByKey = new HashMap<>();
	private static int cachedDimension = Integer.MIN_VALUE;
	private static long cachedAtMs = Long.MIN_VALUE;

	private WaypointCache() {
		// static helper
	}

	@Nonnull
	public static synchronized List<MapWaypoint> get(final int dimensionId) {
		final long now = System.currentTimeMillis();
		if ( dimensionId != cachedDimension
		  || now - cachedAtMs > SCAN_TTL_MS ) {
			cachedWaypoints.clear();
			cachedWaypoints.addAll(WaypointDiscovery.getWaypoints(dimensionId));
			cachedDimension = dimensionId;
			cachedAtMs = now;
		}
		return new ArrayList<>(cachedWaypoints);
	}

	public static synchronized void invalidate() {
		cachedAtMs = Long.MIN_VALUE;
	}

	@Nonnull
	public static String key(@Nonnull final MapWaypoint waypoint) {
		return waypoint.source + '|' + waypoint.name + '|' + waypoint.x + '|' + waypoint.y + '|' + waypoint.z;
	}

	@Nullable
	public static synchronized WaypointStatus getStatus(@Nonnull final MapWaypoint waypoint) {
		return statusByKey.get(key(waypoint));
	}

	public static synchronized void setStatus(@Nonnull final String key, @Nonnull final WaypointStatus status) {
		statusByKey.put(key, status);
	}

	public static synchronized void clearStatuses() {
		statusByKey.clear();
	}
}
