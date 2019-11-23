package com.nisovin.magicspells.util;

import java.io.File;
import java.io.FileOutputStream;

import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.Predicate;

import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.ChatColor;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import com.nisovin.magicspells.MagicSpells;
import com.nisovin.magicspells.DebugHandler;
import com.nisovin.magicspells.util.itemreader.*;
import com.nisovin.magicspells.util.CastUtil.CastMode;
import com.nisovin.magicspells.materials.MagicMaterial;
import com.nisovin.magicspells.materials.ItemNameResolver.ItemTypeAndData;
import com.nisovin.magicspells.util.itemreader.alternative.AlternativeReaderManager;
import com.nisovin.magicspells.util.managers.AttributeManager;

import org.apache.commons.math3.util.FastMath;

public class Util {

	public static Map<String, ItemStack> predefinedItems = new HashMap<>();

	private static Random random = new Random();
	public static int getRandomInt(int bound) {
		return random.nextInt(bound);
	}

	/**
	 * Format is<br />
	 *
	 * <code>itemID#color;enchant-level+enchant-level+enchant-level...|name|lore|lore...</code><p />
	 *
	 * OR<p>
	 *
	 * <code>predefined item key</code><br />
	 *
	 * @param string The string to resolve to an item
	 *
	 * @return the item stack represented by the string
	 */
	public static ItemStack getItemStackFromString(String string) {
		try {
			if (predefinedItems.containsKey(string)) return predefinedItems.get(string).clone();

			ItemStack item;
			String s = string;
			String name = null;
			String[] lore = null;
			HashMap<Enchantment, Integer> enchants = null;
			int color = -1;
			if (s.contains("|")) {
				String[] temp = s.split("\\|");
				s = temp[0];
				if (temp.length == 1) {
					name = "";
				} else {
					name = ChatColor.translateAlternateColorCodes('&', temp[1].replace("__", " "));
					if (temp.length > 2) {
						lore = Arrays.copyOfRange(temp, 2, temp.length);
						for (int i = 0; i < lore.length; i++) {
							lore[i] = ChatColor.translateAlternateColorCodes('&', lore[i].replace("__", " "));
						}
					}
				}
			}
			if (s.contains(";")) {
				String[] temp = s.split(";", 2);
				s = temp[0];
				enchants = new HashMap<>();
				if (!temp[1].isEmpty()) {
					String[] split = temp[1].split("\\+");
					for (int i = 0; i < split.length; i++) {
						String[] enchantData = split[i].split("-");
						Enchantment ench;
						ench = MagicValues.Enchantments.getEnchantmentType(enchantData[0]);
						if (ench == null) continue;
						if (RegexUtil.matches(RegexUtil.BASIC_DECIMAL_INT_PATTERN, enchantData[1])) {
							enchants.put(ench, Integer.parseInt(enchantData[1]));
						}
					}
				}
			}
			if (s.contains("#")) {
				String[] temp = s.split("#");
				s = temp[0];
				if (RegexUtil.matches(RegexUtil.BASIC_HEX_PATTERN, temp[1])) {
					color = Integer.parseInt(temp[1], 16);
				}
			}
			ItemTypeAndData itemTypeAndData = MagicSpells.getItemNameResolver().resolve(s);
			if (itemTypeAndData != null) {
				//item = new ItemStack(itemTypeAndData.id, 1, itemTypeAndData.data);
				// FIXME
				item = new ItemStack(itemTypeAndData.material, 1);
			} else {
				return null;
			}
			if (name != null || lore != null || color >= 0) {
				try {
					ItemMeta meta = item.getItemMeta();
					if (name != null) meta.setDisplayName(name);
					if (lore != null) meta.setLore(Arrays.asList(lore));
					if (color >= 0 && meta instanceof LeatherArmorMeta) ((LeatherArmorMeta)meta).setColor(Color.fromRGB(color));
					item.setItemMeta(meta);
				} catch (Exception e) {
					MagicSpells.error("Failed to process item meta for item: " + s);
				}
			}
			if (enchants != null) {
				if (!enchants.isEmpty()) {
					item.addUnsafeEnchantments(enchants);
				} else {
					item = ItemUtil.addFakeEnchantment(item);
				}
			}
			return item;
		} catch (Exception e) {
			MagicSpells.handleException(e);
			return null;
		}
	}

	// TODO add a blockstate handler
	// TODO add a spawneggmeta handler
	// TODO finish this
	/**
	 * Item config format:
	 *
	 * # Required for all items
	 * # WRITE EXPLANATION OF 'type'
	 * type:
	 *
	 * # Applicable to all items
	 * # WRITE EXPLANATION OF 'name'
	 * name: string
	 * # WRITE EXPLANATION OF 'lore'
	 * lore:
	 *     - lore line 1
	 *     - lore line 2
	 *     - etc
	 * # WRITE EXPLANATION OF 'enchants'
	 * enchants:
	 *     - enchant 1
	 *     - enchant 2
	 *     - etc
	 *
	 * # Used for leather armor
	 * # WRITE EXPLANATION OF 'color'
	 * color:
	 *
	 * # Applicable to potions
	 * # WRITE EXPLANATION OF 'potioneffects'
	 * potioneffects:
	 *     - effect 1
	 *     - effect 2
	 * # Applicable in versions ___ and later
	 * # WRITE EXPLANATION OF 'potioncolor'
	 * potioncolor:
	 *
	 * # Applicable to skulls
	 * # NOTE: SKULLS ARE CURRENTLY BUGGED
	 * # WRITE EXPLANATION OF 'skullowner'
	 * skullowner:
	 * # WRITE EXPLANATION OF 'uuid'
	 * uuid:
	 * # WRITE EXPLANATION OF 'texture'
	 * texture:
	 * # WRITE EXPLANATION OF 'signature'
	 * signature:
	 *
	 * # Used for repairable items
	 * # WRITE EXPLANATION OF 'repaircost'
	 * repaircost: integer
	 *
	 * # Used for written books
	 * # WRITE EXPLANATION OF 'title'
	 * title: String
	 * # WRITE EXPLANATION OF 'author'
	 * author: String
	 * # WRITE EXPLANATION OF 'pages'
	 * pages:
	 *     - page 1 contents
	 *     - page 2 contents
	 *     - etc
	 *
	 * # Applicable to banners
	 * # WRITE EXPLANATION OF 'color'
	 * color:
	 * # WRITE EXPLANATION OF 'patterns'
	 * patterns:
	 *     - pattern 1
	 *     - pattern 2
	 *
	 * # Applicable to all items
	 * # WRITE EXPLANATION OF 'hide-tooltip'
	 * hide-tooltip: true|false
	 *
	 * # Applicable to all items with durability
	 * # WRITE EXPLANATION OF 'unbreakable'
	 * unbreakable: true|false
	 *
	 * # Applicable to all items
	 * # WRITE EXPLANATION OF 'attributes'
	 * attributes:
	 */
	public static ItemStack getItemStackFromConfig(ConfigurationSection config) {
		try {
			// It MUST have a type option
			if (!config.contains("type")) return null;

			// See if this is managed by an alternative reader
			ItemStack item = AlternativeReaderManager.deserialize(config);
			if (item != null) return item;

			// Basic item
			MagicMaterial material = MagicSpells.getItemNameResolver().resolveItem(config.getString("type"));
			if (material == null) return null;
			item = material.toItemStack();
			ItemMeta meta = item.getItemMeta();

			// Name and lore
			meta = NameHandler.process(config, meta);
			meta = LoreHandler.process(config, meta);

			// Enchants
			boolean emptyEnchants = false;
			if (config.contains("enchants") && config.isList("enchants")) {
				List<String> enchants = config.getStringList("enchants");
				for (String enchant : enchants) {
					String[] data = enchant.split(" ");
					Enchantment e = MagicValues.Enchantments.getEnchantmentType(data[0]);
					if (e == null) MagicSpells.error('\'' + data[0] + "' could not be connected to an enchantment");
					if (e != null) {
						int level = 0;
						if (data.length > 1) {
							try {
								level = Integer.parseInt(data[1]);
							} catch (NumberFormatException ex) {
								DebugHandler.debugNumberFormat(ex);
							}
						}
						if (meta instanceof EnchantmentStorageMeta) {
							((EnchantmentStorageMeta)meta).addStoredEnchant(e, level, true);
						} else {
							meta.addEnchant(e, level, true);
						}
					}
				}
				if (enchants.isEmpty()) emptyEnchants = true;
			}

			// Armor color
			meta = LeatherArmorHandler.process(config, meta);

			// Potioneffects
			// Potioncolor
			meta = PotionHandler.process(config, meta);

			// Skull owner
			meta = SkullHandler.process(config, meta);

			// Flower pot
			/*if (config.contains("flower") && item.getType() == Material.FLOWER_POT && meta instanceof BlockStateMeta) {
				MagicMaterial flower = MagicSpells.getItemNameResolver().resolveBlock(config.getString("flower"));
				BlockState state = ((BlockStateMeta)meta).getBlockState();
				MaterialData data = state.getData();
				if (data instanceof FlowerPot) {
					((FlowerPot)data).setContents(new MaterialData(flower.getMaterial()));
				}
				state.setData(data);
				((BlockStateMeta)meta).setBlockState(state);
			}*/

			// Durability
			meta = DurabilityHandler.process(config, meta);

			// Repair cost
			meta = RepairableHandler.process(config, meta);

			// Written book
			meta = WrittenBookHandler.process(config, meta);

			// Banner
			meta = BannerHandler.process(config, meta);

			// Unbreakable
			if (config.getBoolean("unbreakable", false)) {
				meta.setUnbreakable(true);
			}

			// Hide tooltip
			if (config.getBoolean("hide-tooltip", MagicSpells.hidePredefinedItemTooltips())) {
				meta.addItemFlags(ItemFlag.values());
			}

			// Empty enchant
			if (emptyEnchants) {
				item = ItemUtil.addFakeEnchantment(item);
			}

			// Set meta
			item.setItemMeta(meta);

			// Attributes
			AttributeManager attributeManager = MagicSpells.getAttributeManager();
			if (config.contains("attributes")) {
				Set<String> attrs = config.getConfigurationSection("attributes").getKeys(false);
				for (String attrName : attrs) {
					String[] attrData = config.getString("attributes." + attrName).split(" ");
					String attrType = attrData[0];
					double attrAmt = 1;
					try {
						attrAmt = Double.parseDouble(attrData[1]);
					} catch (NumberFormatException e) {
						DebugHandler.debugNumberFormat(e);
					}
					AttributeUtil.AttributeOperation operation = AttributeUtil.AttributeOperation.ADD_NUMBER;
					if (attrData.length > 2) {
						String attrDataLowercase = attrData[2].toLowerCase();
						if (attrDataLowercase.startsWith("mult")) {
							operation = AttributeUtil.AttributeOperation.MULTIPLY_SCALAR_1; // Multiply percent
						} else if (attrDataLowercase.contains("add") && (attrDataLowercase.contains("perc") || attrDataLowercase.contains("scal"))) {
							operation = AttributeUtil.AttributeOperation.ADD_SCALAR; // Add percent
						}
					}
					EquipmentSlot slot = null; // Can be null for all slots
					if (attrData.length > 3) {
						try {
							slot = EquipmentSlot.valueOf(attrData[3].toUpperCase());
						} catch (Exception ex) {
							/* do nothing */
						}
					}

					Attribute attribute = AttributeUtil.AttributeType.getAttribute(attrType);
					AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), attrType, attrAmt, operation.toBukkitOperation(), slot);
					attributeManager.addItemAttribute(item, attribute, modifier);
				}
			}

			return item;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// Just checks to see if the passed string could be lore data
	public static boolean isLoreData(String line) {
		if (line == null) return false;
		line = ChatColor.stripColor(line);
		return line.startsWith("MS$:");
	}

	public static void setLoreData(ItemStack item, String data) {
		ItemMeta meta = item.getItemMeta();
		List<String> lore;
		if (meta.hasLore()) {
			lore = meta.getLore();
			if (!lore.isEmpty()) {
				for (int i = 0; i < lore.size(); i++) {
					if (!isLoreData(lore.get(i))) continue;
					lore.remove(i);
					break;
				}
			}
		} else {
			lore = new ArrayList<>();
		}
		lore.add(ChatColor.BLACK.toString() + ChatColor.MAGIC.toString() + "MS$:" + data);
		meta.setLore(lore);
		item.setItemMeta(meta);
	}

	public static String getLoreData(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		if (meta != null && meta.hasLore()) {
			List<String> lore = meta.getLore();
			if (!lore.isEmpty()) {
				for (int i = 0; i < lore.size(); i++) {
					String s = ChatColor.stripColor(lore.get(lore.size() - 1));
					if (s.startsWith("MS$:")) return s.substring(4);
				}
			}
		}
		return null;
	}

	public static void removeLoreData(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		List<String> lore;
		if (meta.hasLore()) {
			lore = meta.getLore();
			if (!lore.isEmpty()) {
				boolean removed = false;
				for (int i = 0; i < lore.size(); i++) {
					String s = ChatColor.stripColor(lore.get(i));
					if (s.startsWith("MS$:")) {
						lore.remove(i);
						removed = true;
						break;
					}
				}
				if (removed) {
					if (!lore.isEmpty()) {
						meta.setLore(lore);
					} else {
						meta.setLore(null);
					}
					item.setItemMeta(meta);
				}
			}
		}
	}

	static Map<String, EntityType> entityTypeMap = new HashMap<>();
	static {
		for (EntityType type : EntityType.values()) {
			if (type != null && type.getName() != null) {
				entityTypeMap.put(type.getName().toLowerCase(), type);
				entityTypeMap.put(type.name().toLowerCase(), type);
				entityTypeMap.put(type.name().toLowerCase().replace("_", ""), type);
			}
		}
		entityTypeMap.put("zombiepig", EntityType.PIG_ZOMBIE);
		entityTypeMap.put("mooshroom", EntityType.MUSHROOM_COW);
		entityTypeMap.put("cat", EntityType.OCELOT);
		entityTypeMap.put("golem", EntityType.IRON_GOLEM);
		entityTypeMap.put("snowgolem", EntityType.SNOWMAN);
		entityTypeMap.put("dragon", EntityType.ENDER_DRAGON);
		Map<String, EntityType> toAdd = new HashMap<>();
		for (Map.Entry<String, EntityType> entry : entityTypeMap.entrySet()) {
			toAdd.put(entry.getKey() + 's', entry.getValue());
		}
		entityTypeMap.putAll(toAdd);
		entityTypeMap.put("endermen", EntityType.ENDERMAN);
		entityTypeMap.put("wolves", EntityType.WOLF);
	}

	public static EntityType getEntityType(String type) {
		if (type.equalsIgnoreCase("player")) return EntityType.PLAYER;
		return entityTypeMap.get(type.toLowerCase());
	}

	public static PotionEffectType getPotionEffectType(String type) {
		return MagicValues.PotionEffect.getPotionEffectType(type.trim());
	}

	public static Enchantment getEnchantmentType(String type) {
		return MagicValues.Enchantments.getEnchantmentType(type);
	}

	public static Particle getParticle(String type) {
		return ParticleUtil.ParticleEffect.getParticle(type);
	}

	public static CastMode getCastMode(String type) {
		return CastMode.getFromString(type);
	}

	public static void setFacing(Player player, Vector vector) {
		Location loc = player.getLocation();
		setLocationFacingFromVector(loc, vector);
		player.teleport(loc);
	}

	public static void setLocationFacingFromVector(Location location, Vector vector) {
		double yaw = getYawOfVector(vector);
		double pitch = FastMath.toDegrees(-FastMath.asin(vector.getY()));
		location.setYaw((float)yaw);
		location.setPitch((float)pitch);
	}

	public static double getYawOfVector(Vector vector) {
		return FastMath.toDegrees(FastMath.atan2(-vector.getX(), vector.getZ()));
	}

	public static boolean arrayContains(int[] array, int value) {
		for (int i : array) {
			if (i == value) return true;
		}
		return false;
	}

	public static boolean arrayContains(String[] array, String value) {
		for (String i : array) {
			if (Objects.equals(i, value)) return true;
		}
		return false;
	}

	public static boolean arrayContains(Object[] array, Object value) {
		for (Object i : array) {
			if (Objects.equals(i, value)) return true;
		}
		return false;
	}

	public static String arrayJoin(String[] array, char with) {
		if (array == null || array.length == 0) return "";
		int len = array.length;
		StringBuilder sb = new StringBuilder(16 + len << 3);
		sb.append(array[0]);
		for (int i = 1; i < len; i++) {
			sb.append(with);
			sb.append(array[i]);
		}
		return sb.toString();
	}

	public static String listJoin(List<String> list) {
		if (list == null || list.isEmpty()) return "";
		int len = list.size();
		StringBuilder sb = new StringBuilder(len * 12);
		sb.append(list.get(0));
		for (int i = 1; i < len; i++) {
			sb.append(' ');
			sb.append(list.get(i));
		}
		return sb.toString();
	}

	public static String[] splitParams(String string, int max) {
		String[] words = string.trim().split(" ");
		if (words.length <= 1) return words;
		ArrayList<String> list = new ArrayList<>();
		char quote = ' ';
		String building = "";

		for (String word : words) {
			if (word.isEmpty()) continue;
			if (max > 0 && list.size() == max - 1) {
				if (!building.isEmpty()) building += " ";
				building += word;
			} else if (quote == ' ') {
				if (word.length() == 1 || (word.charAt(0) != '"' && word.charAt(0) != '\'')) {
					list.add(word);
				} else {
					quote = word.charAt(0);
					if (quote == word.charAt(word.length() - 1)) {
						quote = ' ';
						list.add(word.substring(1, word.length() - 1));
					} else {
						building = word.substring(1);
					}
				}
			} else {
				if (word.charAt(word.length() - 1) == quote) {
					list.add(building + ' ' + word.substring(0, word.length() - 1));
					building = "";
					quote = ' ';
				} else {
					building += ' ' + word;
				}
			}
		}
		if (!building.isEmpty()) {
			list.add(building);
		}
		return list.toArray(new String[list.size()]);
	}

	public static String[] splitParams(String string) {
		return splitParams(string, 0);
	}

	public static String[] splitParams(String[] split, int max) {
		return splitParams(arrayJoin(split, ' '), max);
	}

	public static String[] splitParams(String[] split) {
		return splitParams(arrayJoin(split, ' '), 0);
	}

	public static int getItemDurability(ItemStack item) {
		ItemMeta meta = item.getItemMeta();
		return meta == null ? 0 : ((Damageable) meta).getDamage();
	}

	public static boolean removeFromInventory(Inventory inventory, ItemStack item) {
		int amt = item.getAmount();
		ItemStack[] items = inventory.getContents();
		for (int i = 0; i < items.length; i++) {
			if (items[i] != null && item.isSimilar(items[i])) {
				if (items[i].getAmount() > amt) {
					items[i].setAmount(items[i].getAmount() - amt);
					amt = 0;
					break;
				} else if (items[i].getAmount() == amt) {
					items[i] = null;
					amt = 0;
					break;
				} else {
					amt -= items[i].getAmount();
					items[i] = null;
				}
			}
		}
		if (amt == 0) {
			inventory.setContents(items);
			return true;
		}
		return false;
	}

	public static boolean removeFromInventory(EntityEquipment entityEquipment, ItemStack item) {
		int amt = item.getAmount();
		ItemStack[] armorContents = entityEquipment.getArmorContents();
		ItemStack[] items = new ItemStack[6];
		for (int i = 0; i < 4; i++) {
			items[i] = armorContents[i];
		}
		items[4] = entityEquipment.getItemInMainHand();
		items[5] = entityEquipment.getItemInOffHand();

		for (int i = 0; i < items.length; i++) {
			if (items[i] == null) continue;
			if (!item.isSimilar(items[i])) continue;

			if (items[i].getAmount() > amt) {
				items[i].setAmount(items[i].getAmount() - amt);
				amt = 0;
				break;
			} else if (items[i].getAmount() == amt) {
				items[i] = null;
				amt = 0;
				break;
			} else {
				amt -= items[i].getAmount();
				items[i] = null;
			}
		}


		if (amt == 0) {
			ItemStack[] updatedArmorContents = new ItemStack[4];
			for (int i = 0; i < 4; i++) {
				updatedArmorContents[i] = items[i];
			}
			entityEquipment.setArmorContents(updatedArmorContents);
			entityEquipment.setItemInMainHand(items[4]);
			entityEquipment.setItemInOffHand(items[5]);
			return true;
		}
		return false;
	}

	public static boolean addToInventory(Inventory inventory, ItemStack item, boolean stackExisting, boolean ignoreMaxStack) {
		int amt = item.getAmount();
		ItemStack[] items = Arrays.copyOf(inventory.getContents(), inventory.getSize());
		if (stackExisting) {
			for (int i = 0; i < items.length; i++) {
				if (items[i] != null && item.isSimilar(items[i])) {
					if (items[i].getAmount() + amt <= items[i].getMaxStackSize()) {
						items[i].setAmount(items[i].getAmount() + amt);
						amt = 0;
						break;
					} else {
						int diff = items[i].getMaxStackSize() - items[i].getAmount();
						items[i].setAmount(items[i].getMaxStackSize());
						amt -= diff;
					}
				}
			}
		}
		if (amt > 0) {
			for (int i = 0; i < items.length; i++) {
				if (items[i] == null) {
					if (amt > item.getMaxStackSize() && !ignoreMaxStack) {
						items[i] = item.clone();
						items[i].setAmount(item.getMaxStackSize());
						amt -= item.getMaxStackSize();
					} else {
						items[i] = item.clone();
						items[i].setAmount(amt);
						amt = 0;
						break;
					}
				}
			}
		}
		if (amt == 0) {
			inventory.setContents(items);
			return true;
		}
		return false;
	}

	public static void rotateVector(Vector v, float degrees) {
		double rad = FastMath.toRadians(degrees);
		double sin = FastMath.sin(rad);
		double cos = FastMath.cos(rad);
		double x = (v.getX() * cos) - (v.getZ() * sin);
		double z = (v.getX() * sin) + (v.getZ() * cos);
		v.setX(x);
		v.setZ(z);
	}

	public static Location applyRelativeOffset(Location loc, Vector relativeOffset) {
		return loc.add(rotateVector(relativeOffset, loc));
	}

	public static Vector rotateVector(Vector v, Location location) {
		return rotateVector(v, location.getYaw(), location.getPitch());
	}

	public static Vector rotateVector(Vector v, float yawDegrees, float pitchDegrees) {
		double yaw = FastMath.toRadians((double)(-1.0F * (yawDegrees + 90.0F)));
		double pitch = FastMath.toRadians((double)(-pitchDegrees));
		double cosYaw = FastMath.cos(yaw);
		double cosPitch = FastMath.cos(pitch);
		double sinYaw = FastMath.sin(yaw);
		double sinPitch = FastMath.sin(pitch);
		double initialX = v.getX();
		double initialY = v.getY();
		double x = initialX * cosPitch - initialY * sinPitch;
		double y = initialX * sinPitch + initialY * cosPitch;
		double initialZ = v.getZ();
		double z = initialZ * cosYaw - x * sinYaw;
		x = initialZ * sinYaw + x * cosYaw;
		return new Vector(x, y, z);
	}

	public static Location applyAbsoluteOffset(Location loc, Vector offset) {
		return loc.add(offset);
	}

	public static Location applyOffsets(Location loc, Vector relativeOffset, Vector absoluteOffset) {
		return applyAbsoluteOffset(applyRelativeOffset(loc, relativeOffset), absoluteOffset);
	}

	public static Location faceTarget(Location origin, Location target) {
		return origin.setDirection(getVectorToTarget(origin, target));
	}

	public static Vector getVectorToTarget(Location origin, Location target) {
		return target.toVector().subtract(origin.toVector());
	}

	public static boolean downloadFile(String url, File file) {
		try {
			URL website = new URL(url);
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			FileOutputStream fos = new FileOutputStream(file);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
			rbc.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public static void createFire(Block block, byte d) {
		block.setType(Material.FIRE);
	}

	public static ItemStack getEggItemForEntityType(EntityType type) {
		Material eggMaterial = null;
		switch (type) {
			case BAT: {
				eggMaterial = Material.BAT_SPAWN_EGG;
				break;
			}
			case BLAZE: {
				eggMaterial = Material.BLAZE_SPAWN_EGG;
				break;
			}
			case CAVE_SPIDER: {
				eggMaterial = Material.CAVE_SPIDER_SPAWN_EGG;
				break;
			}
			case CHICKEN: {
				eggMaterial = Material.CHICKEN_SPAWN_EGG;
				break;
			}
			case COD: {
				eggMaterial = Material.COD_SPAWN_EGG;
				break;
			}
			case COW: {
				eggMaterial = Material.COW_SPAWN_EGG;
				break;
			}
			case CREEPER: {
				eggMaterial = Material.CREEPER_SPAWN_EGG;
				break;
			}
			case DOLPHIN: {
				eggMaterial = Material.DOLPHIN_SPAWN_EGG;
				break;
			}
			case DONKEY: {
				eggMaterial = Material.DONKEY_SPAWN_EGG;
				break;
			}
			case DROWNED: {
				eggMaterial = Material.DROWNED_SPAWN_EGG;
				break;
			}
			case ELDER_GUARDIAN: {
				eggMaterial = Material.ELDER_GUARDIAN_SPAWN_EGG;
				break;
			}
			case ENDERMAN: {
				eggMaterial = Material.ENDERMAN_SPAWN_EGG;
				break;
			}
			case ENDERMITE: {
				eggMaterial = Material.ENDERMITE_SPAWN_EGG;
				break;
			}
			case EVOKER: {
				eggMaterial = Material.EVOKER_SPAWN_EGG;
				break;
			}
			case GHAST: {
				eggMaterial = Material.GHAST_SPAWN_EGG;
				break;
			}
			case GUARDIAN: {
				eggMaterial = Material.GUARDIAN_SPAWN_EGG;
				break;
			}
			case HORSE: {
				eggMaterial = Material.HORSE_SPAWN_EGG;
				break;
			}
			case HUSK: {
				eggMaterial = Material.HUSK_SPAWN_EGG;
				break;
			}
			case LLAMA: {
				eggMaterial = Material.LLAMA_SPAWN_EGG;
				break;
			}
			case MAGMA_CUBE: {
				eggMaterial = Material.MAGMA_CUBE_SPAWN_EGG;
				break;
			}
			case MUSHROOM_COW: {
				eggMaterial = Material.MOOSHROOM_SPAWN_EGG;
				break;
			}
			case MULE: {
				eggMaterial = Material.MULE_SPAWN_EGG;
				break;
			}
			case OCELOT: {
				eggMaterial = Material.OCELOT_SPAWN_EGG;
				break;
			}
			case PARROT: {
				eggMaterial = Material.PARROT_SPAWN_EGG;
				break;
			}
			case PHANTOM: {
				eggMaterial = Material.PHANTOM_SPAWN_EGG;
				break;
			}
			case PIG: {
				eggMaterial = Material.PIG_SPAWN_EGG;
				break;
			}
			case POLAR_BEAR: {
				eggMaterial = Material.POLAR_BEAR_SPAWN_EGG;
				break;
			}
			case PUFFERFISH: {
				eggMaterial = Material.PUFFERFISH_SPAWN_EGG;
				break;
			}
			case RABBIT: {
				eggMaterial = Material.RABBIT_SPAWN_EGG;
				break;
			}
			case SALMON: {
				eggMaterial = Material.SALMON_SPAWN_EGG;
				break;
			}
			case SHEEP: {
				eggMaterial = Material.SHEEP_SPAWN_EGG;
				break;
			}
			case SHULKER: {
				eggMaterial = Material.SHULKER_SPAWN_EGG;
				break;
			}
			case SILVERFISH: {
				eggMaterial = Material.SILVERFISH_SPAWN_EGG;
				break;
			}
			case SKELETON: {
				eggMaterial = Material.SKELETON_SPAWN_EGG;
				break;
			}
			case SKELETON_HORSE: {
				eggMaterial = Material.SKELETON_HORSE_SPAWN_EGG;
				break;
			}
			case SLIME: {
				eggMaterial = Material.SLIME_SPAWN_EGG;
				break;
			}
			case SPIDER: {
				eggMaterial = Material.SPIDER_SPAWN_EGG;
				break;
			}
			case SQUID: {
				eggMaterial = Material.SQUID_SPAWN_EGG;
				break;
			}
			case STRAY: {
				eggMaterial = Material.STRAY_SPAWN_EGG;
				break;
			}
			case TROPICAL_FISH: {
				eggMaterial = Material.TROPICAL_FISH_SPAWN_EGG;
				break;
			}
			case TURTLE: {
				eggMaterial = Material.TURTLE_SPAWN_EGG;
				break;
			}
			case VEX: {
				eggMaterial = Material.VEX_SPAWN_EGG;
				break;
			}
			case VILLAGER: {
				eggMaterial = Material.VILLAGER_SPAWN_EGG;
				break;
			}
			case VINDICATOR: {
				eggMaterial = Material.VINDICATOR_SPAWN_EGG;
				break;
			}
			case WITCH: {
				eggMaterial = Material.WITCH_SPAWN_EGG;
				break;
			}
			case WITHER_SKELETON: {
				eggMaterial = Material.WITHER_SKELETON_SPAWN_EGG;
				break;
			}
			case WOLF: {
				eggMaterial = Material.WOLF_SPAWN_EGG;
				break;
			}
			case ZOMBIE: {
				eggMaterial = Material.ZOMBIE_SPAWN_EGG;
				break;
			}
			case ZOMBIE_HORSE: {
				eggMaterial = Material.ZOMBIE_HORSE_SPAWN_EGG;
				break;
			}
			case PIG_ZOMBIE: {
				eggMaterial = Material.ZOMBIE_PIGMAN_SPAWN_EGG;
				break;
			}
			case ZOMBIE_VILLAGER: {
				eggMaterial = Material.ZOMBIE_VILLAGER_SPAWN_EGG;
				break;
			}
		}

		if (eggMaterial == null) return null;

		return new ItemStack(eggMaterial);
	}

	private static Map<String, String> uniqueIds = new HashMap<>();

	public static String getUniqueId(Player player) {
		String uid = player.getUniqueId().toString().replace("-", "");
		uniqueIds.put(player.getName(), uid);
		return uid;
	}

	public static String getUniqueId(String playerName) {
		if (uniqueIds.containsKey(playerName)) return uniqueIds.get(playerName);
		Player player = Bukkit.getPlayerExact(playerName);
		if (player != null) return getUniqueId(player);
		return null;
	}

	public static String flattenLineBreaks(String raw) {
		return raw.replaceAll("\n", "\\n");
	}

	public static <T> boolean containsParallel(Collection<T> elements, Predicate<? super T> predicate) {
		return elements.parallelStream().anyMatch(predicate);
	}

	public static <T> boolean containsValueParallel(Map<?, T> map, Predicate<? super T> predicate) {
		return containsParallel(map.values(), predicate);
	}

	public static <T> void forEachOrdered(Collection<T> collection, Consumer<? super T> consumer) {
		collection.stream().forEachOrdered(consumer);
	}

	public static <T> void forEachValueOrdered(Map<?, T> map, Consumer<? super T> consumer) {
		forEachOrdered(map.values(), consumer);
	}

	public static void forEachPlayerOnline(Consumer<? super Player> consumer) {
		forEachOrdered(Bukkit.getOnlinePlayers(), consumer);
	}

	public static int clampValue(int min, int max, int value) {
		if (value < min) return min;
		if (value > max) return max;
		return value;
	}

	public static <C extends Collection<Material>> C getMaterialList(List<String> strings, Supplier<C> supplier) {
		C ret = supplier.get();
		strings.forEach(string -> {
			ret.add(Material.matchMaterial(string));
		});
		return ret;
	}

	public static <E extends Enum<E>> E enumValueSafe(Class<E> clazz, String name) {
		try {
			return Enum.valueOf(clazz, name);
		} catch (Exception e) {
			return null;
		}
	}

	public static double getMaxHealth(LivingEntity entity) {
		return entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
	}

	public static void setMaxHealth(LivingEntity entity, double maxHealth) {
		entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
	}

	public static Vector makeFinite(Vector vector) {
		double x = vector.getX();
		double y = vector.getY();
		double z = vector.getZ();

		if (Double.isNaN(x)) x = 0.0D;
		if (Double.isNaN(y)) y = 0.0D;
		if (Double.isNaN(z)) z = 0.0D;

		if (Double.isInfinite(x)) {
			boolean negative = (x < 0.0D);
			x = negative ? -1 : 1;
		}

		if (Double.isInfinite(y)) {
			boolean negative = (y < 0.0D);
			y = negative ? -1 : 1;
		}

		if (Double.isInfinite(z)) {
			boolean negative = (z < 0.0D);
			z = negative ? -1 : 1;
		}

		return new Vector(x, y, z);
	}

}
