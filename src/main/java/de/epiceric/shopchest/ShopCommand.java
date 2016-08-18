package de.epiceric.shopchest;

import de.epiceric.shopchest.config.Regex;
import de.epiceric.shopchest.event.ShopPreCreateEvent;
import de.epiceric.shopchest.event.ShopPreInfoEvent;
import de.epiceric.shopchest.event.ShopPreRemoveEvent;
import de.epiceric.shopchest.event.ShopReloadEvent;
import de.epiceric.shopchest.language.LanguageUtils;
import de.epiceric.shopchest.language.LocalizedMessage;
import de.epiceric.shopchest.nms.JsonBuilder;
import de.epiceric.shopchest.shop.Shop;
import de.epiceric.shopchest.shop.Shop.ShopType;
import de.epiceric.shopchest.utils.ClickType;
import de.epiceric.shopchest.utils.ClickType.EnumClickType;
import de.epiceric.shopchest.utils.ShopUtils;
import de.epiceric.shopchest.utils.UpdateChecker;
import de.epiceric.shopchest.utils.UpdateChecker.UpdateCheckerResult;
import de.epiceric.shopchest.utils.Utils;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

class ShopCommand extends BukkitCommand {

    private ShopChest plugin;
    private Permission perm;
    private ShopUtils shopUtils;

    ShopCommand(ShopChest plugin, String name, String description, String usageMessage, List<String> aliases) {
        super(name, description, usageMessage, aliases);
        this.plugin = plugin;
        this.perm = plugin.getPermission();
        this.shopUtils = plugin.getShopUtils();
    }

    /**
     * Register a command to ShopChest
     *
     * @param command Command to register
     * @param plugin  Instance of ShopChest
     * @throws ReflectiveOperationException
     */
    static void registerCommand(Command command, ShopChest plugin) throws ReflectiveOperationException {
        plugin.debug("Registering command " + command.getName());

        Method commandMap = plugin.getServer().getClass().getMethod("getCommandMap");
        Object cmdmap = commandMap.invoke(plugin.getServer());
        Method register = cmdmap.getClass().getMethod("register", String.class, Command.class);
        register.invoke(cmdmap, command.getName(), command);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player) {
            Player p = (Player) sender;

            if (args.length == 0) {
                sendBasicHelpMessage(p);
                return true;
            } else {
                if (args[0].equalsIgnoreCase("create")) {
                    if (perm.has(p, "shopchest.create")) {
                        if (args.length == 4) {
                            create(args, ShopType.NORMAL, p);
                            return true;
                        } else if (args.length == 5) {
                            if (args[4].equalsIgnoreCase("normal")) {
                                create(args, ShopType.NORMAL, p);
                                return true;
                            } else if (args[4].equalsIgnoreCase("admin")) {
                                if (perm.has(p, "shopchest.create.admin")) {
                                    create(args, ShopType.ADMIN, p);
                                    return true;
                                } else {
                                    p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_CREATE_ADMIN));
                                    return true;
                                }
                            } else {
                                sendBasicHelpMessage(p);
                                return true;
                            }
                        } else {
                            sendBasicHelpMessage(p);
                            return true;
                        }
                    } else {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_CREATE));
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("remove")) {
                    remove(p);
                    return true;
                } else if (args[0].equalsIgnoreCase("info")) {
                    info(p);
                    return true;
                } else if (args[0].equalsIgnoreCase("reload")) {
                    if (perm.has(p, "shopchest.reload")) {
                        reload(p);
                        return true;
                    } else {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_RELOAD));
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("update")) {
                    if (perm.has(p, "shopchest.update")) {
                        checkUpdates(p);
                        return true;
                    } else {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_UPDATE));
                        return true;
                    }
                } else if (args[0].equalsIgnoreCase("limits")) {
                    if (perm.has(p, "shopchest.limits")) {
                        plugin.debug(p.getName() + " is viewing his shop limits: " + shopUtils.getShopAmount(p) + "/" + shopUtils.getShopLimit(p));
                        int limit = shopUtils.getShopLimit(p);
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.OCCUPIED_SHOP_SLOTS,
                                new LocalizedMessage.ReplacedRegex(Regex.LIMIT, (limit < 0 ? "∞" : String.valueOf(limit))),
                                new LocalizedMessage.ReplacedRegex(Regex.AMOUNT, String.valueOf(shopUtils.getShopAmount(p)))));

                        return true;
                    } else {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_LIMITS));
                    }
                } else if (args[0].equalsIgnoreCase("config")) {
                    if (perm.has(p, "shopchest.config")) {
                        if (args.length >= 4) {
                            plugin.debug(p.getName() + " is changing the configuration");

                            String property = args[2];
                            String value = args[3];

                            if (args[1].equalsIgnoreCase("set")) {
                                plugin.getShopChestConfig().set(property, value);
                                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CHANGED_CONFIG_SET, new LocalizedMessage.ReplacedRegex(Regex.PROPERTY, property), new LocalizedMessage.ReplacedRegex(Regex.VALUE, value)));
                                return true;
                            } else if (args[1].equalsIgnoreCase("add")) {
                                plugin.getShopChestConfig().add(property, value);
                                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CHANGED_CONFIG_ADDED, new LocalizedMessage.ReplacedRegex(Regex.PROPERTY, property), new LocalizedMessage.ReplacedRegex(Regex.VALUE, value)));
                                return true;
                            } else if (args[1].equalsIgnoreCase("remove")) {
                                plugin.getShopChestConfig().remove(property, value);
                                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CHANGED_CONFIG_REMOVED, new LocalizedMessage.ReplacedRegex(Regex.PROPERTY, property), new LocalizedMessage.ReplacedRegex(Regex.VALUE, value)));
                                return true;
                            } else {
                                sendBasicHelpMessage(p);
                                return true;
                            }
                        } else {
                            sendBasicHelpMessage(p);
                            return true;
                        }
                    } else {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_PERMISSION_CONFIG));
                        return true;
                    }
                } else {
                    sendBasicHelpMessage(p);
                    return true;
                }

                return true;
            }

        } else {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Only players can execute this command.");
            return true;
        }

    }

    /**
     * A given player checks for updates
     * @param player The command executor
     */
    private void checkUpdates(Player player) {
        plugin.debug(player.getName() + " is checking for updates");

        player.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CHECKING));

        UpdateChecker uc = new UpdateChecker(ShopChest.getInstance());
        UpdateCheckerResult result = uc.check();

        if (result == UpdateCheckerResult.TRUE) {
            plugin.setLatestVersion(uc.getVersion());
            plugin.setDownloadLink(uc.getLink());
            plugin.setUpdateNeeded(true);

            JsonBuilder jb = new JsonBuilder(plugin, LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_AVAILABLE, new LocalizedMessage.ReplacedRegex(Regex.VERSION, uc.getVersion())), LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_CLICK_TO_DOWNLOAD), uc.getLink());
            jb.sendJson(player);

        } else if (result == UpdateCheckerResult.FALSE) {
            plugin.setLatestVersion("");
            plugin.setDownloadLink("");
            plugin.setUpdateNeeded(false);
            player.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_NO_UPDATE));
        } else {
            plugin.setLatestVersion("");
            plugin.setDownloadLink("");
            plugin.setUpdateNeeded(false);
            player.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.UPDATE_ERROR));
        }
    }

    /**
     * A given player reloads the shops
     * @param player The command executor
     */
    private void reload(Player player) {
        plugin.debug(player.getName() + " is reloading the shops");

        ShopReloadEvent event = new ShopReloadEvent(player);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()){
            plugin.debug("Reload event cancelled");
            return;
        }

        int count = shopUtils.reloadShops(true);
        plugin.debug(player.getName() + " has reloaded " + count + " shops");
        player.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.RELOADED_SHOPS, new LocalizedMessage.ReplacedRegex(Regex.AMOUNT, String.valueOf(count))));
    }

    /**
     * A given player creates a shop
     * @param args Arguments of the entered command
     * @param shopType The {@link ShopType}, the shop will have
     * @param p The command executor
     */
    private void create(String[] args, ShopType shopType, Player p) {
        plugin.debug(p.getName() + " wants to create a shop");

        int amount;
        double buyPrice, sellPrice;

        int limit = shopUtils.getShopLimit(p);

        if (limit != -1) {
            if (shopUtils.getShopAmount(p) >= limit) {
                if (shopType != ShopType.ADMIN || !plugin.getShopChestConfig().exclude_admin_shops) {
                    p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.SHOP_LIMIT_REACHED, new LocalizedMessage.ReplacedRegex(Regex.LIMIT, String.valueOf(limit))));
                    return;
                }
            }
        }

        plugin.debug(p.getName() + " has not reached the limit");

        try {
            amount = Integer.parseInt(args[1]);
            buyPrice = Double.parseDouble(args[2]);
            sellPrice = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.AMOUNT_PRICE_NOT_NUMBER));
            return;
        }

        plugin.debug(p.getName() + " has entered numbers as prices and amount");

        if (!plugin.getShopChestConfig().allow_decimals_in_price && (buyPrice != (int) buyPrice || sellPrice != (int) sellPrice)) {
            p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.PRICES_CONTAIN_DECIMALS));
            return;
        }

        plugin.debug(p.getName() + " has entered the numbers correctly (according to allow-decimals configuration)");

        boolean buyEnabled = !(buyPrice <= 0), sellEnabled = !(sellPrice <= 0);

        if (!buyEnabled && !sellEnabled) {
            p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.BUY_SELL_DISABLED));
            return;
        }

        plugin.debug(p.getName() + " has enabled buying, selling or both");

        if (Utils.getPreferredItemInHand(p) == null) {
            p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.NO_ITEM_IN_HAND));
            return;
        }

        plugin.debug(p.getName() + " has an item in his hand");

        for (String item : plugin.getShopChestConfig().blacklist) {

            ItemStack itemStack;

            if (item.contains(":")) {
                itemStack = new ItemStack(Material.getMaterial(item.split(":")[0]), 1, Short.parseShort(item.split(":")[1]));
            } else {
                itemStack = new ItemStack(Material.getMaterial(item), 1);
            }

            if (itemStack.getType().equals(Utils.getPreferredItemInHand(p).getType()) && itemStack.getDurability() == Utils.getPreferredItemInHand(p).getDurability()) {
                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CANNOT_SELL_ITEM));
                return;
            }
        }

        plugin.debug(p.getName() + "'s item is not on the blacklist");

        for (String key : plugin.getShopChestConfig().minimum_prices) {

            ItemStack itemStack;
            double price = plugin.getConfig().getDouble("minimum-prices." + key);

            if (key.contains(":")) {
                itemStack = new ItemStack(Material.getMaterial(key.split(":")[0]), 1, Short.parseShort(key.split(":")[1]));
            } else {
                itemStack = new ItemStack(Material.getMaterial(key), 1);
            }

            if (itemStack.getType().equals(Utils.getPreferredItemInHand(p).getType()) && itemStack.getDurability() == Utils.getPreferredItemInHand(p).getDurability()) {
                if (buyEnabled) {
                    if ((buyPrice <= amount * price) && (buyPrice > 0)) {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.BUY_PRICE_TOO_LOW, new LocalizedMessage.ReplacedRegex(Regex.MIN_PRICE, String.valueOf(amount * price))));
                        return;
                    }
                }

                if (sellEnabled) {
                    if ((sellPrice <= amount * price) && (sellPrice > 0)) {
                        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.SELL_PRICE_TOO_LOW, new LocalizedMessage.ReplacedRegex(Regex.MIN_PRICE, String.valueOf(amount * price))));
                        return;
                    }
                }
            }
        }

        plugin.debug(p.getName() + "'s prices are higher than the minimum");

        if (sellEnabled && buyEnabled) {
            if (plugin.getShopChestConfig().buy_greater_or_equal_sell) {
                if (buyPrice < sellPrice) {
                    p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.BUY_PRICE_TOO_LOW, new LocalizedMessage.ReplacedRegex(Regex.MIN_PRICE, String.valueOf(sellPrice))));
                    return;
                }
            }
        }

        plugin.debug(p.getName() + "'s buy price is high enough");

        ItemStack itemStack = new ItemStack(Utils.getPreferredItemInHand(p).getType(), amount, Utils.getPreferredItemInHand(p).getDurability());
        itemStack.setItemMeta(Utils.getPreferredItemInHand(p).getItemMeta());

        if (Enchantment.DURABILITY.canEnchantItem(itemStack)) {
            if (itemStack.getDurability() > 0 && !plugin.getShopChestConfig().allow_broken_items) {
                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CANNOT_SELL_BROKEN_ITEM));
                return;
            }
        }

        plugin.debug(p.getName() + "'s item is not broken (or broken items are allowed through config)");

        double creationPrice = (shopType == ShopType.NORMAL) ? plugin.getShopChestConfig().shop_creation_price_normal : plugin.getShopChestConfig().shop_creation_price_admin;
        if (creationPrice > 0) {
            if (plugin.getEconomy().getBalance(p) < creationPrice) {
                p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.SHOP_CREATE_NOT_ENOUGH_MONEY, new LocalizedMessage.ReplacedRegex(Regex.CREATION_PRICE, String.valueOf(creationPrice))));
                return;
            }
        }

        plugin.debug(p.getName() + " can pay the creation price");

        ShopPreCreateEvent event = new ShopPreCreateEvent(p, Shop.createImaginaryShop(p, itemStack, null, buyPrice, sellPrice, shopType));
        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            ClickType.setPlayerClickType(p, new ClickType(EnumClickType.CREATE, itemStack, buyPrice, sellPrice, shopType));
            plugin.debug(p.getName() + " can now click a chest");
            p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CLICK_CHEST_CREATE));
        } else {
            plugin.debug("Shop pre create event cancelled");
        }
    }

    /**
     * A given player removes a shop
     * @param p The command executor
     */
    private void remove(Player p) {
        plugin.debug(p.getName() + " wants to remove a shop");

        ShopPreRemoveEvent event = new ShopPreRemoveEvent(p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Shop pre remove event cancelled");
            return;
        }

        plugin.debug(p.getName() + " can now click a chest");
        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CLICK_CHEST_REMOVE));
        ClickType.setPlayerClickType(p, new ClickType(EnumClickType.REMOVE));
    }

    /**
     * A given player retrieves information about a shop
     * @param p The command executor
     */
    private void info(Player p) {
        plugin.debug(p.getName() + " wants to retrieve information");

        ShopPreInfoEvent event = new ShopPreInfoEvent(p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            plugin.debug("Shop pre info event cancelled");
            return;
        }

        plugin.debug(p.getName() + " can now click a chest");
        p.sendMessage(LanguageUtils.getMessage(LocalizedMessage.Message.CLICK_CHEST_INFO));
        ClickType.setPlayerClickType(p, new ClickType(EnumClickType.INFO));
    }

    /**
     * Sends the basic help message to a given player
     * @param player Player who will receive the message
     */
    private void sendBasicHelpMessage(Player player) {
        plugin.debug("Sending basic help message to " + player.getName());

        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " create <amount> <buy-price> <sell-price> [normal|admin] - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_CREATE));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " remove - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_REMOVE));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " info - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_INFO));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " reload - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_RELOAD));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " update - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_UPDATE));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " limits - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_LIMITS));
        player.sendMessage(ChatColor.GREEN + "/" + plugin.getShopChestConfig().main_command_name + " config <set|add|remove> <property> <value> - " + LanguageUtils.getMessage(LocalizedMessage.Message.COMMAND_DESC_CONFIG));
    }

}
