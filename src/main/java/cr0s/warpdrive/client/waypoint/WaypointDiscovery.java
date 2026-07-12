package cr0s.warpdrive.client.waypoint;

import cr0s.warpdrive.WarpDrive;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Reads client waypoint mods without linking WarpDrive to any of their APIs.
 */
@SideOnly(Side.CLIENT)
public final class WaypointDiscovery {

	private static final Object MISSING = new Object();
	private static final Set<String> WARNED_FAILURES = new HashSet<>();

	private WaypointDiscovery() {
	}

	/**
	 * Returns enabled waypoints belonging to {@code currentDimension}.
	 */
	public static List<MapWaypoint> getWaypoints(final int currentDimension) {
		final Set<MapWaypoint> waypoints = new TreeSet<>(
				Comparator.comparing((MapWaypoint waypoint) -> waypoint.source)
				          .thenComparing(waypoint -> waypoint.name)
				          .thenComparingInt(waypoint -> waypoint.x)
				          .thenComparingInt(waypoint -> waypoint.y)
				          .thenComparingInt(waypoint -> waypoint.z));

		if (Loader.isModLoaded("voxelmap") || isClassPresent("com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap")) {
			try {
				collectVoxelMap(waypoints, currentDimension);
			} catch (final Exception | LinkageError throwable) {
				warnProvider("VoxelMap", throwable);
			}
		}

		if (Loader.isModLoaded("xaerominimap")) {
			try {
				collectXaero(waypoints, currentDimension);
			} catch (final Exception | LinkageError throwable) {
				warnProvider("Xaero's Minimap", throwable);
			}
		}

		if (Loader.isModLoaded("journeymap")) {
			try {
				collectJourneyMap(waypoints, currentDimension);
			} catch (final Exception | LinkageError throwable) {
				warnProvider("JourneyMap", throwable);
			}
		}

		return new ArrayList<>(waypoints);
	}

	private static void collectVoxelMap(final Set<MapWaypoint> result, final int currentDimension) throws Exception {
		final Class<?> voxelMapClass = findClass(
				"com.mamiyaotaru.voxelmap.interfaces.AbstractVoxelMap",
				"com.mamiyaotaru.voxelmap.VoxelMap");
		final Object voxelMap = readStaticRequired(voxelMapClass,
				"getInstance", "getVoxelMapInstance", "instance");
		if (voxelMap == null) {
			return;
		}

		final Object manager = readRequired(voxelMap, "getWaypointManager", "waypointManager");
		final Object rawWaypoints = readRequired(manager, "getWaypoints", "wayPts", "waypoints");
		for (final Object waypoint : snapshot(rawWaypoints)) {
			try {
				if (waypoint != null
				 && readRequiredBoolean(waypoint, "isEnabled", "getEnabled", "enabled")
				 && isInDimension(waypoint, currentDimension)) {
					result.add(toVoxelMapWaypoint(waypoint, currentDimension));
				}
			} catch (final Exception exception) {
				warnProvider("VoxelMap waypoint", exception);
			}
		}
	}

	private static void collectXaero(final Set<MapWaypoint> result, final int currentDimension) throws Exception {
		Object root;
		try {
			final Class<?> sessionClass = findClass("xaero.common.XaeroMinimapSession");
			root = readStaticRequired(sessionClass, "getCurrentSession", "instance");
		} catch (final ReflectiveOperationException exception) {
			final Class<?> minimapClass = findClass("xaero.minimap.XaeroMinimap");
			root = readStaticRequired(minimapClass, "getInstance", "instance");
		}
		if (root == null) {
			return;
		}

		final Object manager = readRequired(root, "getWaypointsManager", "waypointsManager");
		final Object world = readRequired(manager, "getCurrentWorld", "currentWorld");
		if (world == null) {
			return;
		}
		final Object worldDimension = readOptional(world, "getDimId", "getDimension", "dimension", "dim");
		if (worldDimension instanceof Number && ((Number) worldDimension).intValue() != currentDimension) {
			return;
		}

		final Object sets = readOptional(world, "getSets", "sets");
		if (sets != MISSING) {
			for (final Object waypointSet : snapshot(sets)) {
				collectXaeroSet(result, waypointSet, currentDimension);
			}
		} else {
			Object currentSet = readOptional(world, "getCurrentSet", "currentSet");
			if (currentSet == MISSING) {
				currentSet = readRequired(manager, "getWaypoints", "waypoints");
			}
			collectXaeroSet(result, currentSet, currentDimension);
		}
		Object serverWaypoints = readOptional(world, "getServerWaypoints", "serverWaypoints");
		if (serverWaypoints == MISSING) {
			serverWaypoints = readOptional(manager, "getServerWaypoints", "serverWaypoints");
		}
		if (serverWaypoints != MISSING && serverWaypoints != null) {
			collectXaeroWaypoints(result, serverWaypoints, currentDimension);
		}
	}

	private static void collectXaeroSet(final Set<MapWaypoint> result, final Object waypointSet,
	                                    final int currentDimension) throws Exception {
		if (waypointSet == null) {
			return;
		}
		final Object rawWaypoints = readRequired(waypointSet, "getList", "getWaypoints", "list", "waypoints");
		collectXaeroWaypoints(result, rawWaypoints, currentDimension);
	}

	private static void collectXaeroWaypoints(final Set<MapWaypoint> result, final Object rawWaypoints,
	                                         final int currentDimension) {
		for (final Object waypoint : snapshot(rawWaypoints)) {
			try {
				if (waypoint == null) {
					continue;
				}
				final Object disabled = readOptional(waypoint, "isDisabled", "getDisabled", "disabled");
				final boolean enabled;
				if (disabled != MISSING) {
					enabled = !asBoolean(disabled, "disabled");
				} else {
					enabled = readRequiredBoolean(waypoint, "isEnabled", "getEnabled", "enabled");
				}
				if (enabled) {
					result.add(toWaypoint("Xaero's Minimap", waypoint));
				}
			} catch (final Exception exception) {
				warnProvider("Xaero waypoint", exception);
			}
		}
	}

	private static MapWaypoint toVoxelMapWaypoint(final Object waypoint, final int dimension) throws Exception {
		final Object nameValue = readRequired(waypoint, "getName", "name");
		if (nameValue == null) {
			throw new IllegalStateException("Waypoint name is null");
		}
		return new MapWaypoint("VoxelMap", String.valueOf(nameValue),
		                       readVoxelMapCoordinate(waypoint, "getX", "x", dimension),
		                       readRequiredInt(waypoint, "getY", "y"),
		                       readVoxelMapCoordinate(waypoint, "getZ", "z", dimension));
	}

	private static int readVoxelMapCoordinate(final Object waypoint, final String getter,
	                                         final String field, final int dimension) throws Exception {
		final Object valueGetter = readOptional(waypoint, getter);
		if (valueGetter instanceof Number) {
			return ((Number) valueGetter).intValue();
		}
		final int raw = readRequiredInt(waypoint, field);
		return dimension == -1 ? raw >> 3 : raw;
	}

	private static void collectJourneyMap(final Set<MapWaypoint> result, final int currentDimension) throws Exception {
		final Class<?> storeClass = findClass(
				"journeymap.client.waypoint.WaypointStore",
				"journeymap.common.waypoint.WaypointStore");
		final Object store = readStaticRequired(storeClass, "getInstance", "INSTANCE", "instance");
		if (store == null) {
			return;
		}

		final Object rawWaypoints = readRequired(store, "getAll", "getAllWaypoints", "waypoints");
		for (final Object waypoint : snapshot(rawWaypoints)) {
			try {
				if (waypoint != null
				 && readRequiredBoolean(waypoint, "isEnable", "isEnabled", "getEnabled", "enable", "enabled")
				 && isInDimension(waypoint, currentDimension)) {
					result.add(toWaypoint("JourneyMap", waypoint));
				}
			} catch (final Exception exception) {
				warnProvider("JourneyMap waypoint", exception);
			}
		}
	}

	private static MapWaypoint toWaypoint(final String source, final Object waypoint) throws Exception {
		final Object nameValue = readRequired(waypoint, "getName", "name");
		if (nameValue == null) {
			throw new IllegalStateException("Waypoint name is null");
		}
		return new MapWaypoint(source, String.valueOf(nameValue),
				readRequiredInt(waypoint, "getX", "x"),
				readRequiredInt(waypoint, "getY", "y"),
				readRequiredInt(waypoint, "getZ", "z"));
	}

	private static boolean isInDimension(final Object waypoint, final int currentDimension) throws Exception {
		final Object dimensions = readOptional(waypoint, "getDimensions", "dimensions");
		if (dimensions != MISSING && dimensions != null) {
			for (final Object dimension : snapshot(dimensions)) {
				if (dimension instanceof Number && ((Number) dimension).intValue() == currentDimension
				 || dimension instanceof String && Integer.toString(currentDimension).equals(dimension)) {
					return true;
				}
			}
			return false;
		}

		final Object dimension = readOptional(waypoint,
				"getDimension", "getDimensionId", "getDimId", "dimension", "dim");
		if (dimension instanceof Number) {
			return ((Number) dimension).intValue() == currentDimension;
		}
		if (dimension instanceof String) {
			return Integer.toString(currentDimension).equals(dimension);
		}

		final Object inDimension = readOptional(waypoint, "isInPlayerDimension", "isInDimension", "inDimension");
		if (inDimension != MISSING) {
			return asBoolean(inDimension, "inDimension");
		}
		throw new NoSuchFieldException("No waypoint dimension property on " + waypoint.getClass().getName());
	}

	private static Class<?> findClass(final String... classNames) throws ClassNotFoundException {
		ClassNotFoundException lastException = null;
		for (final String className : classNames) {
			try {
				final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
				return Class.forName(className, false, contextClassLoader == null
				                                     ? WaypointDiscovery.class.getClassLoader() : contextClassLoader);
			} catch (final ClassNotFoundException exception) {
				lastException = exception;
			}
		}
		throw lastException == null ? new ClassNotFoundException() : lastException;
	}

	private static boolean isClassPresent(final String className) {
		try {
			findClass(className);
			return true;
		} catch (final ClassNotFoundException exception) {
			return false;
		}
	}

	private static Object readStaticRequired(final Class<?> type, final String... names) throws Exception {
		for (final String name : names) {
			final Method method = findNoArgMethod(type, name);
			if (method != null && Modifier.isStatic(method.getModifiers())) {
				return invoke(method, null);
			}
			final Field field = findField(type, name);
			if (field != null && Modifier.isStatic(field.getModifiers())) {
				return field.get(null);
			}
		}
		throw new NoSuchFieldException("No static member on " + type.getName());
	}

	private static Object readRequired(final Object owner, final String... names) throws Exception {
		final Object value = readOptional(owner, names);
		if (value == MISSING) {
			throw new NoSuchFieldException("No matching member on " + owner.getClass().getName());
		}
		return value;
	}

	private static Object readOptional(final Object owner, final String... names) throws Exception {
		if (owner == null) {
			return MISSING;
		}
		for (final String name : names) {
			final Method method = findNoArgMethod(owner.getClass(), name);
			if (method != null && !Modifier.isStatic(method.getModifiers())) {
				return invoke(method, owner);
			}
			final Field field = findField(owner.getClass(), name);
			if (field != null && !Modifier.isStatic(field.getModifiers())) {
				return field.get(owner);
			}
		}
		return MISSING;
	}

	private static Method findNoArgMethod(final Class<?> type, final String name) {
		try {
			return type.getMethod(name);
		} catch (final NoSuchMethodException ignored) {
			Class<?> current = type;
			while (current != null) {
				try {
					final Method method = current.getDeclaredMethod(name);
					method.setAccessible(true);
					return method;
				} catch (final NoSuchMethodException exception) {
					current = current.getSuperclass();
				}
			}
			return null;
		}
	}

	private static Field findField(final Class<?> type, final String name) {
		Class<?> current = type;
		while (current != null) {
			try {
				final Field field = current.getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (final NoSuchFieldException exception) {
				current = current.getSuperclass();
			}
		}
		return null;
	}

	private static Object invoke(final Method method, final Object owner) throws Exception {
		try {
			return method.invoke(owner);
		} catch (final InvocationTargetException exception) {
			final Throwable cause = exception.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw exception;
		}
	}

	private static List<Object> snapshot(final Object values) {
		if (values == null) {
			return Collections.emptyList();
		}
		final Collection<?> collection;
		if (values instanceof Map<?, ?>) {
			collection = ((Map<?, ?>) values).values();
		} else if (values instanceof Collection<?>) {
			collection = (Collection<?>) values;
		} else {
			throw new IllegalArgumentException("Not a waypoint collection: " + values.getClass().getName());
		}
		return new ArrayList<Object>(collection);
	}

	private static int readRequiredInt(final Object owner, final String... names) throws Exception {
		final Object value = readRequired(owner, names);
		if (!(value instanceof Number)) {
			throw new IllegalArgumentException("Expected a number on " + owner.getClass().getName());
		}
		return ((Number) value).intValue();
	}

	private static boolean readRequiredBoolean(final Object owner, final String... names) throws Exception {
		return asBoolean(readRequired(owner, names), names[0]);
	}

	private static boolean asBoolean(final Object value, final String name) {
		if (!(value instanceof Boolean)) {
			throw new IllegalArgumentException("Expected boolean for " + name);
		}
		return (Boolean) value;
	}

	private static void warnProvider(final String provider, final Throwable throwable) {
		final String signature = provider + ':' + throwable.getClass().getName() + ':' + String.valueOf(throwable.getMessage());
		if (!WARNED_FAILURES.add(signature)) {
			return;
		}
		WarpDrive.logger.warn(String.format("Unable to read %s waypoints: %s: %s",
				provider, throwable.getClass().getSimpleName(), String.valueOf(throwable.getMessage())));
	}

	/** Neutral immutable representation of a map waypoint. */
	public static final class MapWaypoint {
		public final String source;
		public final String name;
		public final int x;
		public final int y;
		public final int z;

		public MapWaypoint(final String source, final String name,
		                   final int x, final int y, final int z) {
			this.source = source;
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}

	}
}
