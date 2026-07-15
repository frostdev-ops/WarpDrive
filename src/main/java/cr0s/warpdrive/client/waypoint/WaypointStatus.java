package cr0s.warpdrive.client.waypoint;

import javax.annotation.Nonnull;

/** Client-side reachability knowledge about a map waypoint, populated from server probe results. */
public final class WaypointStatus {

	public enum State {
		UNKNOWN,
		PENDING,
		REACHABLE,
		REJECTED
	}

	public final State state;
	public final String reasonKey;
	public final int landingY;
	public final long updatedAtMs;

	public WaypointStatus(@Nonnull final State state, @Nonnull final String reasonKey, final int landingY) {
		this.state = state;
		this.reasonKey = reasonKey;
		this.landingY = landingY;
		this.updatedAtMs = System.currentTimeMillis();
	}
}
