package com.gmail.val59000mc.bridge;

import com.gmail.val59000mc.utils.VersionUtils;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class DefaultVersionBridge implements VersionBridge {

	private static final Logger LOGGER = Logger.getLogger(DefaultVersionBridge.class.getCanonicalName());
	private static boolean loggedReadOnlyScoreWarning = false;
	private static volatile boolean actionBarUnsupported = false;

	@Override
	public void setWorldBorder(World world, double centerX, double centerZ, double sideLength) {
		world.getWorldBorder().setCenter(centerX, centerZ);
		world.getWorldBorder().setSize(sideLength);
	}

	@Override
	public void setGameRule(World world, String gameRule, Object value) {
		VersionUtils.getVersionUtils().setGameRuleValue(world, gameRule, value);
	}

	@Override
	public boolean setLocatorBar(World world, boolean enabled) {
		try {
			GameRule<?> rule = GameRule.getByName("locatorBar");
			if (rule != null) {
				VersionUtils.getVersionUtils().setGameRuleValue(world, "locatorBar", enabled);
				return true;
			}
		} catch (Throwable ignored) {
			// Not supported on older versions
		}
		return false;
	}

	@Override
	public void setSpawnChunkRadius(World world, int radius) {
		if (VersionUtils.isAtLeast(20, 5)) {
			try {
				GameRule<?> rule = GameRule.getByName("spawnChunkRadius");
				if (rule != null) {
					VersionUtils.getVersionUtils().setGameRuleValue(world, "spawnChunkRadius", radius);
				}
			} catch (Throwable ignored) {
				// Not supported on older versions
			}
		}
	}

	@Override
	public void setReadOnlyPlayerScore(Score score, int value) {
		try {
			score.setScore(value);
		} catch (IllegalStateException e) {
			try {
				Object craftScoreboard = score.getScoreboard();
				Method getHandleScoreboard = craftScoreboard.getClass().getMethod("getHandle");
				Object nmsScoreboard = getHandleScoreboard.invoke(craftScoreboard);

				Object craftObjective = score.getObjective();
				Method getHandleObjective = craftObjective.getClass().getMethod("getHandle");
				Object nmsObjective = getHandleObjective.invoke(craftObjective);

				Method getOrCreateScore = null;
				for (Method m : nmsScoreboard.getClass().getMethods()) {
					Class<?>[] params = m.getParameterTypes();
					if (params.length == 3 && params[2] == boolean.class && params[1].isAssignableFrom(nmsObjective.getClass())) {
						getOrCreateScore = m;
						break;
					}
					if (params.length == 2 && params[1].isAssignableFrom(nmsObjective.getClass())) {
						getOrCreateScore = m;
						break;
					}
				}
				if (getOrCreateScore == null) {
					for (Method m : nmsScoreboard.getClass().getDeclaredMethods()) {
						Class<?>[] params = m.getParameterTypes();
						if (params.length == 3 && params[2] == boolean.class && params[1].isAssignableFrom(nmsObjective.getClass())) {
							m.setAccessible(true);
							getOrCreateScore = m;
							break;
						}
						if (params.length == 2 && params[1].isAssignableFrom(nmsObjective.getClass())) {
							m.setAccessible(true);
							getOrCreateScore = m;
							break;
						}
					}
				}

				if (getOrCreateScore != null) {
					Class<?> holderParam = getOrCreateScore.getParameterTypes()[0];
					Object holder;
					if (holderParam == String.class) {
						holder = score.getEntry();
					} else {
						holder = Proxy.newProxyInstance(
								holderParam.getClassLoader(),
								new Class<?>[]{holderParam},
								(proxy, method, args) -> {
									if (method.getReturnType() == String.class) {
										return score.getEntry();
									}
									if ("equals".equals(method.getName()) && args != null && args.length == 1) {
										return proxy == args[0];
									}
									if ("hashCode".equals(method.getName())) {
										return score.getEntry().hashCode();
									}
									if ("toString".equals(method.getName())) {
										return score.getEntry();
									}
									return null;
								}
						);
					}

					Object scoreAccess;
					if (getOrCreateScore.getParameterCount() == 3) {
						scoreAccess = getOrCreateScore.invoke(nmsScoreboard, holder, nmsObjective, true);
					} else {
						scoreAccess = getOrCreateScore.invoke(nmsScoreboard, holder, nmsObjective);
					}

					boolean scoreSet = false;
					for (Method m : scoreAccess.getClass().getMethods()) {
						if ((m.getReturnType() == void.class || m.getReturnType() == int.class)
								&& m.getParameterCount() == 1
								&& m.getParameterTypes()[0] == int.class) {
							m.invoke(scoreAccess, value);
							scoreSet = true;
							break;
						}
					}
					if (!scoreSet) {
						for (Method m : scoreAccess.getClass().getDeclaredMethods()) {
							if ((m.getReturnType() == void.class || m.getReturnType() == int.class)
									&& m.getParameterCount() == 1
									&& m.getParameterTypes()[0] == int.class) {
								m.setAccessible(true);
								m.invoke(scoreAccess, value);
								break;
							}
						}
					}
				}
			} catch (Exception ex) {
				if (!loggedReadOnlyScoreWarning) {
					loggedReadOnlyScoreWarning = true;
					LOGGER.log(Level.WARNING, "Failed to update read-only health score via reflection on this server version (suppressing further warnings): " + ex.getMessage());
				}
			}
		}
	}

	@Override
	public Objective registerObjective(Scoreboard scoreboard, String name, String criteria, String displayName, String renderType) {
		return VersionUtils.getVersionUtils().registerNewObjective(scoreboard, name, criteria, displayName, renderType);
	}

	@Override
	public void setTeamNameTagVisibility(Team team, boolean visible) {
		VersionUtils.getVersionUtils().setTeamNameTagVisibility(team, visible);
	}

	@Override
	public void setPlayerMaxHealth(Player player, double maxHealth) {
		VersionUtils.getVersionUtils().setPlayerMaxHealth(player, maxHealth);
	}

	@Override
	public void setEntityAI(LivingEntity entity, boolean ai) {
		VersionUtils.getVersionUtils().setEntityAI(entity, ai);
	}

	@Override
	public ShapedRecipe createShapedRecipe(ItemStack result, String key) {
		return VersionUtils.getVersionUtils().createShapedRecipe(result, key);
	}

	@Override
	public ItemStack createPlayerSkull(String name, UUID uuid) {
		return VersionUtils.getVersionUtils().createPlayerSkull(name, uuid);
	}

	@Override
	public void sendActionBar(Player player, String message) {
		if (actionBarUnsupported) {
			return;
		}
		try {
			player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
		} catch (LinkageError e) {
			// Old Spigot (1.8.8) has no ChatMessageType overload. Falling back to chat here would
			// flood it, NoClean pushes a countdown every second, so just go quiet from now on.
			actionBarUnsupported = true;
			LOGGER.log(Level.FINE, "Action bar not supported on this server, skipping it", e);
		} catch (Throwable t) {
			player.sendMessage(message);
		}
	}

	@Override
	public boolean hasEndPortalEye(Block block) {
		return VersionUtils.getVersionUtils().hasEye(block);
	}

	@Override
	public void setEndPortalEye(Block block, boolean eye) {
		VersionUtils.getVersionUtils().setEye(block, eye);
	}

	@Override
	public void setEndPortalFrameFacing(Block block, BlockFace facing) {
		VersionUtils.getVersionUtils().setEndPortalFrameOrientation(block, facing);
	}
}
