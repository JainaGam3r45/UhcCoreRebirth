package com.gmail.val59000mc.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.scoreboard.Score;

/**
 * Writes a value into a score whose criteria is read-only (health, mostly).
 * <p>
 * Since 1.20.3 {@code Score#setScore} throws on those criteria (MC-163943), but vanilla only
 * pushes the health score when it changes, so a fresh scoreboard shows nothing until the player
 * takes damage. The only way to seed it is the NMS scoreboard, which accepts a "force writable"
 * flag: {@code getOrCreatePlayerScore(ScoreHolder, Objective, boolean)}.
 * <p>
 * Everything is resolved by reflection and cached. Paper 1.20.5+ runs Mojang names, Spigot
 * 1.20.3/1.20.4 runs obfuscated ones, so names are tried first and the signature shape second.
 */
public final class ReadOnlyScoreWriter {

	private static final Logger LOGGER = Logger.getLogger(ReadOnlyScoreWriter.class.getCanonicalName());

	// Null until the first read-only write. A failed lookup is cached as well so we don't redo it on every tick.
	private static volatile Resolved resolved;
	private static volatile boolean unavailable;

	private ReadOnlyScoreWriter() {
	}

	public static void set(Score score, int value) {
		try {
			score.setScore(value);
			return;
		} catch (IllegalStateException ignored) {
			// Read-only criteria, handled below
		}

		if (unavailable) {
			return;
		}

		try {
			Resolved r = resolved;
			if (r == null) {
				r = resolve(score);
				resolved = r;
			}
			r.write(score, value);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
			unavailable = true;
			Throwable cause = ex instanceof InvocationTargetException && ex.getCause() != null ? ex.getCause() : ex;
			LOGGER.log(Level.WARNING, "Couldn't seed the read-only health score (" + cause.getClass().getSimpleName() + ": "
					+ cause.getMessage() + "). Hearts on tab or below the name may stay empty until a player's health changes.");
			LOGGER.log(Level.FINE, "Read-only score failure details", cause);
		}
	}

	private static Resolved resolve(Score score) throws ReflectiveOperationException {
		Object bukkitScoreboard = score.getScoreboard();
		Object bukkitObjective = score.getObjective();

		// CraftScoreboard.getHandle() is public, CraftObjective.getHandle() is package-private
		Method scoreboardHandle = findNoArgMethod(bukkitScoreboard.getClass(), "getHandle");
		Method objectiveHandle = findNoArgMethod(bukkitObjective.getClass(), "getHandle");

		Class<?> nmsScoreboardClass = scoreboardHandle.getReturnType();
		Class<?> nmsObjectiveClass = objectiveHandle.getReturnType();

		Method getOrCreate = findGetOrCreateScore(nmsScoreboardClass, nmsObjectiveClass);
		Method setValue = findScoreSetter(getOrCreate.getReturnType());
		Method holderFactory = findHolderFactory(getOrCreate.getParameterTypes()[0]);

		return new Resolved(scoreboardHandle, objectiveHandle, getOrCreate, setValue, holderFactory);
	}

	private static Method findNoArgMethod(Class<?> type, String name) throws NoSuchMethodException {
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			try {
				Method method = c.getDeclaredMethod(name);
				method.setAccessible(true);
				return method;
			} catch (NoSuchMethodException ignored) {
				// Keep walking up
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + name + "()");
	}

	private static Method findGetOrCreateScore(Class<?> nmsScoreboard, Class<?> nmsObjective) throws NoSuchMethodException {
		Method byShape = null;
		for (Class<?> c = nmsScoreboard; c != null; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				Class<?>[] params = m.getParameterTypes();
				boolean shapeMatches = params.length == 3
						&& params[2] == boolean.class
						&& params[1].isAssignableFrom(nmsObjective)
						&& !Modifier.isStatic(m.getModifiers())
						&& hasVoidIntSetter(m.getReturnType());
				if (!shapeMatches) {
					continue;
				}
				m.setAccessible(true);
				if (m.getName().equals("getOrCreatePlayerScore")) {
					return m;
				}
				if (byShape == null) {
					byShape = m;
				}
			}
		}
		if (byShape == null) {
			throw new NoSuchMethodException(nmsScoreboard.getName() + ".getOrCreatePlayerScore(ScoreHolder, Objective, boolean)");
		}
		return byShape;
	}

	private static boolean hasVoidIntSetter(Class<?> scoreAccess) {
		for (Method m : scoreAccess.getMethods()) {
			if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
				return true;
			}
		}
		return false;
	}

	private static Method findScoreSetter(Class<?> scoreAccess) throws NoSuchMethodException {
		Method fallback = null;
		for (Method m : scoreAccess.getMethods()) {
			if (m.getReturnType() == void.class && m.getParameterCount() == 1 && m.getParameterTypes()[0] == int.class) {
				// "add" returns the new value so it's excluded by the void check, "set" is the one left
				if (m.getName().equals("set")) {
					return m;
				}
				if (fallback == null) {
					fallback = m;
				}
			}
		}
		if (fallback == null) {
			throw new NoSuchMethodException(scoreAccess.getName() + ".set(int)");
		}
		return fallback;
	}

	// ScoreHolder.forNameOnly(String): a holder that only carries the entry name, which is all the scoreboard keys on
	private static Method findHolderFactory(Class<?> scoreHolder) throws NoSuchMethodException {
		Method byShape = null;
		for (Method m : scoreHolder.getDeclaredMethods()) {
			if (Modifier.isStatic(m.getModifiers())
					&& m.getParameterCount() == 1
					&& m.getParameterTypes()[0] == String.class
					&& scoreHolder.isAssignableFrom(m.getReturnType())) {
				if (m.getName().equals("forNameOnly")) {
					return m;
				}
				if (byShape == null) {
					byShape = m;
				}
			}
		}
		if (byShape == null) {
			throw new NoSuchMethodException(scoreHolder.getName() + ".forNameOnly(String)");
		}
		return byShape;
	}

	private static final class Resolved {
		private final Method scoreboardHandle;
		private final Method objectiveHandle;
		private final Method getOrCreate;
		private final Method setValue;
		private final Method holderFactory;

		private Resolved(Method scoreboardHandle, Method objectiveHandle, Method getOrCreate, Method setValue, Method holderFactory) {
			this.scoreboardHandle = scoreboardHandle;
			this.objectiveHandle = objectiveHandle;
			this.getOrCreate = getOrCreate;
			this.setValue = setValue;
			this.holderFactory = holderFactory;
		}

		private void write(Score score, int value) throws ReflectiveOperationException {
			Object nmsScoreboard = scoreboardHandle.invoke(score.getScoreboard());
			Object nmsObjective = objectiveHandle.invoke(score.getObjective());
			Object holder = holderFactory.invoke(null, score.getEntry());
			Object access = getOrCreate.invoke(nmsScoreboard, holder, nmsObjective, true);
			setValue.invoke(access, value);
		}
	}
}
