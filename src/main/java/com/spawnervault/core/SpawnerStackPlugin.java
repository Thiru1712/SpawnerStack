package com.spawnervault.core;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** SpawnerStack v1.1.4 -  */
public final class SpawnerStackPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<String, SpawnerData> active = new ConcurrentHashMap<>();
    private final Map<UUID, MenuSession> menus = new HashMap<>();
    private final Map<String, DetachedSpawner> detached = new HashMap<>();
    private final Random random = new Random();
    private File dataFile, pricesFile, detachedFile;
    private YamlConfiguration data, prices, detachedYaml;
    private NamespacedKey typeKey, stackKey, tokenKey, adminLocKey, blockMarkerKey, blockTypeKey, blockStackKey;
    private long lastProduction;
    private Economy economy;

    @Override public void onEnable() {
        typeKey = new NamespacedKey(this, "mob_type");
        stackKey = new NamespacedKey(this, "stack");
        tokenKey = new NamespacedKey(this, "detached_token");
        adminLocKey = new NamespacedKey(this, "admin_spawner_location");
        blockMarkerKey = new NamespacedKey(this, "managed_spawner");
        blockTypeKey = new NamespacedKey(this, "managed_type");
        blockStackKey = new NamespacedKey(this, "managed_stack");
        saveDefaultConfig();
        ensureFile("prices.yml"); ensureFile("spawners.yml"); ensureFile("messages.yml");
        dataFile = new File(getDataFolder(), "data.yml"); detachedFile = new File(getDataFolder(), "detached.yml"); pricesFile = new File(getDataFolder(), "prices.yml");
        data = YamlConfiguration.loadConfiguration(dataFile); detachedYaml = YamlConfiguration.loadConfiguration(detachedFile); prices = YamlConfiguration.loadConfiguration(pricesFile);
        loadActive(); loadDetached();
        setupEconomy();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("spawnerstack")).setExecutor(this);
        lastProduction = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskTimer(this, this::productionTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 20L * Math.max(10, getConfig().getInt("production.save-interval-seconds", 30)), 20L * Math.max(10, getConfig().getInt("production.save-interval-seconds", 30)));
        getLogger().info("SpawnerStack enabled. Loaded " + active.size() + " active spawners.");
    }
    @Override public void onDisable() { saveAll(); for (Player p : Bukkit.getOnlinePlayers()) if (menus.containsKey(p.getUniqueId())) p.closeInventory(); }
    private void ensureFile(String name) { File f = new File(getDataFolder(), name); if (!f.exists()) saveResource(name, false); }
    private void setupEconomy() {
        economy = null;
        if (!getConfig().getBoolean("economy.enabled", true)) return;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().info("Vault not found. Sell Loot is unavailable until Vault and a compatible economy provider are installed.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            getLogger().info("No Vault economy provider found. Sell Loot is currently unavailable.");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("Economy integration enabled: " + economy.getName());
    }

    private String locKey(Location l) { return l.getWorld().getName()+":"+l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ(); }
    private Location parseLoc(String k) { try { String[] a=k.split(":",2), b=a[1].split(","); World w=Bukkit.getWorld(a[0]); return w==null?null:new Location(w,Integer.parseInt(b[0]),Integer.parseInt(b[1]),Integer.parseInt(b[2])); } catch(Exception e){return null;} }
    private boolean validSpawnerType(EntityType t) {
        return t == EntityType.ZOMBIE
                || t == EntityType.SKELETON
                || t == EntityType.SPIDER
                || t == EntityType.CAVE_SPIDER
                || t == EntityType.SILVERFISH
                || t == EntityType.BLAZE
                || t == EntityType.MAGMA_CUBE;
    }

    private boolean isTrialSpawner(Block block) {
        return block != null && block.getType() == Material.TRIAL_SPAWNER;
    }
    private int stackMax(){ return Math.max(1, getConfig().getInt("production.max-stack",64)); }
    private long slotCapacity(SpawnerData d){ return (long)getConfig().getInt("production.base-slots",36)*d.stack; }
    private int maxStack(Material m){ return m.getMaxStackSize() > 0 ? m.getMaxStackSize() : 1; }
    private long usedSlots(SpawnerData d){ long n=0; for(Map.Entry<String,Long> e:d.loot.entrySet()){ Material m=Material.matchMaterial(e.getKey()); if(m!=null) n += (e.getValue()+maxStack(m)-1)/maxStack(m); } return n; }
    private String msg(String key){ File f=new File(getDataFolder(),"messages.yml"); YamlConfiguration y=YamlConfiguration.loadConfiguration(f); return ChatColor.translateAlternateColorCodes('&',y.getString(key,key)); }
    private String fmt(String s, SpawnerData d){ return s.replace("{type}",pretty(d.type)).replace("{stack}",String.valueOf(d.stack)); }
    private String pretty(String s){ return s.toLowerCase(Locale.ROOT).replace('_',' '); }

    private ItemStack spawnerItem(SpawnerData d){
        ItemStack i=new ItemStack(Material.SPAWNER); ItemMeta m=i.getItemMeta();
        m.setDisplayName(ChatColor.GOLD+pretty(d.type)+" Spawner "+ChatColor.WHITE+"×"+d.stack);
        m.setLore(List.of(ChatColor.GRAY+"Virtual production",ChatColor.GRAY+"Stack: "+d.stack+"/"+stackMax(),ChatColor.YELLOW+"Right-click"+ChatColor.GRAY+" to place/open"));
        PersistentDataContainer p=m.getPersistentDataContainer(); p.set(typeKey,PersistentDataType.STRING,d.type); p.set(stackKey,PersistentDataType.INTEGER,d.stack);
        String token=UUID.randomUUID().toString(); p.set(tokenKey,PersistentDataType.STRING,token); detached.put(token,new DetachedSpawner(copy(d))); i.setItemMeta(m); return i;
    }
    private EntityType itemType(ItemStack i){ if(i==null||i.getType()!=Material.SPAWNER||!i.hasItemMeta())return null; String s=i.getItemMeta().getPersistentDataContainer().get(typeKey,PersistentDataType.STRING); try{return s==null?null:EntityType.valueOf(s);}catch(Exception e){return null;} }
    private int itemStack(ItemStack i){ if(i==null||!i.hasItemMeta())return 1; return Math.max(1,i.getItemMeta().getPersistentDataContainer().getOrDefault(stackKey,PersistentDataType.INTEGER,1)); }
    private String itemToken(ItemStack i){ return i!=null&&i.hasItemMeta()?i.getItemMeta().getPersistentDataContainer().get(tokenKey,PersistentDataType.STRING):null; }

    @EventHandler public void place(BlockPlaceEvent e){
        Block block = e.getBlockPlaced();
        if(block.getType()!=Material.SPAWNER || isTrialSpawner(block)) return;
        ItemStack source=e.getItemInHand();
        if (!validSpawnerType(itemType(source))) return;
        adoptSpawner(block, e.getPlayer(), source, true);
        SpawnerData placed=active.get(locKey(block.getLocation()));
        if(placed==null) return;
        mergeWithAdjacent(block,placed);
    }

    private void mergeWithAdjacent(Block placedBlock, SpawnerData placed){
        Block[] neighbors={placedBlock.getRelative(BlockFace.NORTH),placedBlock.getRelative(BlockFace.SOUTH),placedBlock.getRelative(BlockFace.EAST),placedBlock.getRelative(BlockFace.WEST),placedBlock.getRelative(BlockFace.UP),placedBlock.getRelative(BlockFace.DOWN)};
        for(Block neighbor:neighbors){
            if(neighbor.getType()!=Material.SPAWNER || isTrialSpawner(neighbor)) continue;
            SpawnerData target=active.get(locKey(neighbor.getLocation()));
            if(target==null) continue;
            if(!target.type.equalsIgnoreCase(placed.type)) continue;
            int room=stackMax()-target.stack;
            if(room<=0) continue;
            int moved=Math.min(room,placed.stack);
            target.stack+=moved;
            placed.stack-=moved;
            markManagedAt(neighbor,target);
            if(placed.stack<=0){
                active.remove(locKey(placedBlock.getLocation()));
                placedBlock.setType(Material.AIR,false);
                saveAll();
                return;
            }
            markManagedAt(placedBlock,placed);
            saveAll();
            return;
        }
    }

    private void adoptSpawner(Block block, Player owner, ItemStack sourceItem, boolean announce){
        if(block == null || block.getType()!=Material.SPAWNER || isTrialSpawner(block)) return;
        BlockState state=block.getState();
        if(!(state instanceof CreatureSpawner cs)) return;
        EntityType itemT=itemType(sourceItem);
        if (!validSpawnerType(itemT)) return;
        EntityType t=itemT;

        String key=locKey(block.getLocation());
        SpawnerData d=active.get(key);
        if(d==null){
            String token=itemToken(sourceItem);
            if(token!=null){ DetachedSpawner ds=detached.remove(token); if(ds!=null)d=copy(ds.data); }
        }
        if(d==null) d=new SpawnerData(t.name(),Math.min(stackMax(),Math.max(1,itemStack(sourceItem))));
        if(owner!=null) d.playerPlaced=true;
        d.type=t.name();
        d.stack=Math.min(stackMax(),Math.max(1,d.stack));
        if(d.lastProduction<=0) d.lastProduction=System.currentTimeMillis();
        if(owner!=null && d.ownerUuid==null){ d.ownerUuid=owner.getUniqueId(); d.ownerName=owner.getName(); }
        if(d.ownerName==null && d.ownerUuid!=null) d.ownerName=Bukkit.getOfflinePlayer(d.ownerUuid).getName();
        if(d.placedAt<=0) d.placedAt=System.currentTimeMillis();
        active.put(key,d);
        markManagedSpawner(cs,d);
        disableVanillaSpawner(cs,t);
        if(announce && owner!=null) owner.sendMessage(color(fmt(msg("placed"),d)));
        saveAll();
    }

    private void markManagedSpawner(CreatureSpawner cs, SpawnerData d){
        PersistentDataContainer p=cs.getPersistentDataContainer();
        p.set(blockMarkerKey,PersistentDataType.BYTE,(byte)1);
        p.set(blockTypeKey,PersistentDataType.STRING,d.type);
        p.set(blockStackKey,PersistentDataType.INTEGER,d.stack);
        cs.update(true,false);
    }

    private void disableVanillaSpawner(CreatureSpawner cs,EntityType t){
        cs.setSpawnedType(t);
        // Keep the vanilla spawner block from running its physical mob-spawn cycle.
        // SpawnerStack production is virtual and is handled by productionTick().
        cs.setDelay(Integer.MAX_VALUE);
        cs.update(true,false);
    }

    @EventHandler public void interact(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;
        if(e.getClickedBlock().getType()!=Material.SPAWNER || isTrialSpawner(e.getClickedBlock())) return;
        String k=locKey(e.getClickedBlock().getLocation()); SpawnerData d=active.get(k); if(d==null)return; e.setCancelled(true); Player p=e.getPlayer();
        ItemStack hand=e.getItem(); EntityType heldType=itemType(hand);
        if(heldType!=null){
            EntityType placedType;
            try{placedType=EntityType.valueOf(d.type);}catch(Exception ex){placedType=null;}
            if(placedType==heldType){
                if(d.stack>=stackMax()){p.sendMessage(color(msg("max-stack")));return;}
                e.setCancelled(true); consumeOne(p,e.getHand()); d.stack++; markManagedAt(e.getClickedBlock(),d); p.sendMessage(color(fmt(msg("stacked"),d))); saveAll(); return;
            }
            p.sendMessage(color(msg("wrong-type"))); return;
        }
        openMain(p,k,d);
    }
    private void markManagedAt(Block block,SpawnerData d){
        BlockState state=block.getState(); if(state instanceof CreatureSpawner cs) markManagedSpawner(cs,d);
    }
    private void consumeOne(Player p,EquipmentSlot h){ ItemStack i=h==EquipmentSlot.OFF_HAND?p.getInventory().getItemInOffHand():p.getInventory().getItemInMainHand(); if(i.getAmount()<=1)i.setAmount(0);else i.setAmount(i.getAmount()-1); }

    @EventHandler public void breakSpawner(BlockBreakEvent e){
        if(e.getBlock().getType()!=Material.SPAWNER || isTrialSpawner(e.getBlock())) return;
        String k=locKey(e.getBlock().getLocation());
        SpawnerData d=active.get(k);
        // Ordinary vanilla/generated spawners are not managed by SpawnerStack.
        if(d==null) return;
        e.setCancelled(true);
        Player p=e.getPlayer();
        ItemStack tool=p.getInventory().getItemInMainHand();
        boolean tier=tool!=null && (tool.getType()==Material.IRON_PICKAXE||tool.getType()==Material.DIAMOND_PICKAXE||tool.getType()==Material.NETHERITE_PICKAXE);
        boolean silk=tier&&tool.containsEnchantment(Enchantment.SILK_TOUCH);
        if(!silk){
            p.sendMessage(color(getConfig().getString("breaking.message","&cCannot break the spawner with this tool.")));
            return;
        }
        active.remove(k);
        e.getBlock().setType(Material.AIR,false);
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(.5,.5,.5),spawnerItem(d));
        p.sendMessage(color(fmt(msg("mined"),d)));
        saveAll();
    }
    @EventHandler public void entityExplode(EntityExplodeEvent e){
        e.blockList().removeIf(block -> block.getType()==Material.SPAWNER && active.containsKey(locKey(block.getLocation())));
    }

    @EventHandler public void blockExplode(BlockExplodeEvent e){
        e.blockList().removeIf(block -> block.getType()==Material.SPAWNER && active.containsKey(locKey(block.getLocation())));
    }

    @EventHandler public void naturalSpawn(SpawnerSpawnEvent e){ if(active.containsKey(locKey(e.getSpawner().getLocation())))e.setCancelled(true); }

    private void productionTick(){ long now=System.currentTimeMillis(); long cooldown=Math.max(1000,Math.min(60,getConfig().getLong("production.cooldown-seconds",30))*1000); long maxCatch=Math.max(1,getConfig().getLong("production.max-offline-catchup-hours",24))*3600000L; int processed=0; for(SpawnerData d:active.values()){
            long elapsed=Math.min(maxCatch,Math.max(0,now-d.lastProduction)); long cycles=elapsed/cooldown; if(cycles<=0)continue; cycles=Math.min(cycles,Math.max(1,getConfig().getInt("production.max-cycles-per-tick",200)));
            for(int c=0;c<cycles;c++) for(int s=0;s<d.stack;s++) virtualRoll(d);
            d.lastProduction += cycles*cooldown; if(++processed>=Math.max(1,getConfig().getInt("production.max-cycles-per-tick",200)))break;
        } lastProduction=now;
    }
    private void addLoot(SpawnerData d,Material m,long amount){ if(m==null||amount<=0)return; long cap=slotCapacity(d); long existing=d.loot.getOrDefault(m.name(),0L); long used=usedSlots(d); long max=maxStack(m); long partial=existing>0 && existing%max!=0 ? max-(existing%max) : 0; long freeSlots=Math.max(0,cap-used); long possible=partial+freeSlots*max; if(possible>0)d.loot.put(m.name(),existing+Math.min(amount,possible)); }
    private void xp(SpawnerData d,int amount){d.xp=Math.max(0,d.xp+amount);}
    private void virtualRoll(SpawnerData d){
        EntityType type;
        try { type = EntityType.valueOf(d.type); } catch (IllegalArgumentException ex) { return; }
        switch(type){
            case ZOMBIE -> {
                addLoot(d,Material.ROTTEN_FLESH,random.nextInt(3));
                if(random.nextDouble()<0.025)addLoot(d,Material.IRON_INGOT,1);
                if(random.nextDouble()<0.025)addLoot(d,random.nextBoolean()?Material.CARROT:Material.POTATO,1);
                xp(d,5);
            }
            case SKELETON -> {
                addLoot(d,Material.BONE,random.nextInt(3));
                addLoot(d,Material.ARROW,random.nextInt(3));
                if(random.nextDouble()<0.085)addLoot(d,Material.BOW,1);
                xp(d,5);
            }
            case SPIDER, CAVE_SPIDER -> {
                addLoot(d,Material.STRING,random.nextInt(3));
                if(random.nextDouble()<0.333)addLoot(d,Material.SPIDER_EYE,1);
                xp(d,5);
            }
            case SILVERFISH -> xp(d,5);
            case BLAZE -> { if(random.nextDouble()<0.5)addLoot(d,Material.BLAZE_ROD,1); xp(d,10); }
            case MAGMA_CUBE -> { if(random.nextDouble()<0.25)addLoot(d,Material.MAGMA_CREAM,1); xp(d,5); }
            default -> {
                // Every normal spawner is still managed by SpawnerStack.
                // Types without a configured vanilla loot profile produce no items by default.
                // This prevents invented/non-vanilla drops while allowing future profiles to be added safely.
            }
        }
    }

    private void openMain(Player p,String k,SpawnerData d){
        Inventory inv=Bukkit.createInventory(null,54,title("&0&lSPAWNER &8• &f"+d.stack+" "+pretty(d.type)+" SPAWNERS"));
        fillFrame(inv);
        long cooldown=Math.max(1000,Math.min(60,getConfig().getLong("production.cooldown-seconds",30))*1000);
        long remaining=Math.max(0,cooldown-(System.currentTimeMillis()-d.lastProduction));
        inv.setItem(4,item(Material.SPAWNER,"&6&l"+pretty(d.type)+" SPAWNER","&8━━━━━━━━━━━━━━━━","&7Stack: &e"+d.stack+" &8/ &e"+stackMax(),"&7Production: &e×"+d.stack,"&7Next cycle: &b"+formatRemaining(remaining),"&7Status: &aACTIVE","&8━━━━━━━━━━━━━━━━","&eRight-click the placed spawner with another","&esame-type SpawnerStack spawner to stack it."));
        inv.setItem(20,item(Material.CHEST,"&a&lSTORAGE","&7Stored slots: &e"+usedSlots(d)+" &8/ &e"+slotCapacity(d),"&7Open virtual loot storage.","","&e▶ OPEN STORAGE"));
        inv.setItem(22,item(Material.HOPPER,"&a&lCOLLECT LOOT","&7Collect all loot your inventory can hold.","&7Anything that does not fit remains stored.","","&e▶ COLLECT"));
        inv.setItem(24,item(Material.EXPERIENCE_BOTTLE,"&b&lCOLLECT XP","&7Stored XP: &e"+d.xp,"&7Collect all stored experience.","","&e▶ COLLECT XP"));
        inv.setItem(31,item(Material.CLOCK,"&e&lPRODUCTION","&7Cooldown: &e"+(cooldown/1000)+" seconds","&7Next cycle in: &b"+formatRemaining(remaining),"&7Production is virtual; no mobs are spawned.",""));
        inv.setItem(33,item(Material.GOLD_INGOT,"&6&lSELL LOOT","&7Sell all sellable stored loot.","&7Uses the server economy through Vault.","","&e▶ SELL"));
        inv.setItem(49,item(Material.BARRIER,"&c&lCLOSE","&7Close"));
        menus.put(p.getUniqueId(),new MenuSession(k,Menu.MAIN,0)); p.openInventory(inv);
    }

    private String formatRemaining(long millis){
        long seconds=Math.max(0,(millis+999)/1000);
        return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }

    private void fillFrame(Inventory inv){
        ItemStack pane=item(Material.BLACK_STAINED_GLASS_PANE,"&0");
        for(int slot=0;slot<54;slot++){
            int row=slot/9,col=slot%9;
            if(row==0||row==5||col==0||col==8) inv.setItem(slot,pane.clone());
        }
    }

    private void openStorage(Player p,String k,SpawnerData d,int page){
        List<ItemStack> items=new ArrayList<>();
        for(Map.Entry<String,Long> e:d.loot.entrySet()){
            Material m=Material.matchMaterial(e.getKey()); if(m==null||e.getValue()<=0)continue;
            long n=e.getValue(); int max=maxStack(m);
            while(n>0){int take=(int)Math.min(max,n); items.add(new ItemStack(m,take)); n-=take;}
        }
        int per=45,total=Math.max(1,(items.size()+per-1)/per); page=Math.max(0,Math.min(page,total-1));
        Inventory inv=Bukkit.createInventory(null,54,title("&0&lSPAWNER &8• &fSTORAGE"));
        int start=page*per; for(int i=0;i<per&&start+i<items.size();i++)inv.setItem(i,items.get(start+i));
        inv.setItem(45,item(Material.ARROW,"&e&lPREVIOUS","&7Page "+(page+1)+"/"+total));
        inv.setItem(49,item(Material.HOPPER,"&a&lCOLLECT ALL","&7Collect everything that fits in your inventory."));
        inv.setItem(53,item(Material.ARROW,"&e&lNEXT","&7Page "+(page+1)+"/"+total));
        inv.setItem(48,item(Material.BARRIER,"&c&lBACK","&7Return to the spawner."));
        menus.put(p.getUniqueId(),new MenuSession(k,Menu.STORAGE,page)); p.openInventory(inv);
    }

    private void openAdminList(Player p,int page){
        if(!p.hasPermission("spawnerstack.admin")){p.sendMessage(color(msg("no-permission")));return;}
        List<String> keys=new ArrayList<>();
        for(Map.Entry<String,SpawnerData> entry:active.entrySet()) if(entry.getValue().playerPlaced) keys.add(entry.getKey());
        keys.sort((a,b)->Long.compare(active.get(b).placedAt,active.get(a).placedAt));
        int per=45,total=Math.max(1,(keys.size()+per-1)/per); page=Math.max(0,Math.min(page,total-1));
        Inventory inv=Bukkit.createInventory(null,54,title("&8Placed Spawners"));
        int start=page*per;
        for(int i=0;i<per&&start+i<keys.size();i++){
            String key=keys.get(start+i); SpawnerData d=active.get(key); if(d==null)continue;
            ItemStack icon=new ItemStack(Material.SPAWNER); ItemMeta meta=icon.getItemMeta();
            meta.setDisplayName(color("&6&l"+pretty(d.type)+" Spawner &e×"+d.stack));
            String owner=d.ownerName==null?"Unknown":d.ownerName;
            meta.setLore(List.of(color("&7Placed by: &f"+owner),color("&7Location: &f"+key),color("&7Placed: &f"+formatDate(d.placedAt)),color("&eClick to inspect")));
            meta.getPersistentDataContainer().set(adminLocKey,PersistentDataType.STRING,key); icon.setItemMeta(meta); inv.setItem(i,icon);
        }
        inv.setItem(45,item(Material.ARROW,"&ePrevious","&7Page "+(page+1)+"/"+total));
        inv.setItem(49,item(Material.BARRIER,"&cClose","&7Total placed: "+keys.size()));
        inv.setItem(53,item(Material.ARROW,"&eNext","&7Page "+(page+1)+"/"+total));
        menus.put(p.getUniqueId(),new MenuSession("__ADMIN_LIST__",Menu.ADMIN_LIST,page)); p.openInventory(inv);
    }

    private void openAdminDetail(Player p,String key){
        if(!p.hasPermission("spawnerstack.admin")){p.sendMessage(color(msg("no-permission")));return;}
        SpawnerData d=active.get(key); Location loc=parseLoc(key); if(d==null||loc==null){p.sendMessage(color("&cThat spawner no longer exists."));openAdminList(p,0);return;}
        Inventory inv=Bukkit.createInventory(null,27,title("&8Spawner Details"));
        String owner=d.ownerName==null?"Unknown":d.ownerName;
        inv.setItem(4,item(Material.SPAWNER,"&6&l"+pretty(d.type)+" Spawner","&7Managed spawner record."));
        inv.setItem(10,item(Material.PLAYER_HEAD,"&e&lPlaced By","&f"+owner,"&7UUID: &f"+(d.ownerUuid==null?"Unknown":d.ownerUuid)));
        inv.setItem(12,item(Material.CHEST,"&a&lSpawner Count","&e×"+d.stack,"&7Maximum: &e×"+stackMax()));
        inv.setItem(14,item(Material.COMPASS,"&b&lLocation","&f"+loc.getWorld().getName(),"&7X: &f"+loc.getBlockX(),"&7Y: &f"+loc.getBlockY(),"&7Z: &f"+loc.getBlockZ()));
        inv.setItem(16,item(Material.CLOCK,"&d&lPlaced","&f"+formatDate(d.placedAt)));
        inv.setItem(20,item(Material.ENDER_PEARL,"&a&lTeleport","&7Teleport to this spawner."));
        inv.setItem(22,item(Material.BOOK,"&e&lOpen Spawner GUI","&7Open the normal management GUI."));
        inv.setItem(24,item(Material.ARROW,"&c&lBack","&7Return to placed spawners."));
        menus.put(p.getUniqueId(),new MenuSession(key,Menu.ADMIN_DETAIL,0)); p.openInventory(inv);
    }

    private String formatDate(long timestamp){
        if(timestamp<=0)return "Unknown";
        return java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").format(java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()));
    }

    private String title(String s){return color(s);}
    private ItemStack item(Material m,String name,String... lore){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.setDisplayName(color(name));x.setLore(Arrays.stream(lore).map(this::color).toList());i.setItemMeta(x);return i;}
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s==null?"":s);}

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return; MenuSession s=menus.get(p.getUniqueId()); if(s==null)return; e.setCancelled(true); e.setResult(Event.Result.DENY);
        if(s.menu==Menu.ADMIN_LIST){
            if(e.getRawSlot()==45){openAdminList(p,s.page-1);return;}
            if(e.getRawSlot()==53){openAdminList(p,s.page+1);return;}
            if(e.getRawSlot()==49){p.closeInventory();return;}
            if(e.getRawSlot()<45){ItemStack clicked=e.getCurrentItem(); if(clicked!=null&&clicked.hasItemMeta()){String key=clicked.getItemMeta().getPersistentDataContainer().get(adminLocKey,PersistentDataType.STRING); if(key!=null)openAdminDetail(p,key);}}
            return;
        }
        SpawnerData d=active.get(s.key); if(d==null){p.closeInventory();return;}
        if(s.menu==Menu.ADMIN_DETAIL){
            if(e.getRawSlot()==20){Location loc=parseLoc(s.key); if(loc!=null){Location tp=loc.clone().add(.5,1.0,.5); tp.setYaw(p.getLocation().getYaw()); tp.setPitch(p.getLocation().getPitch()); p.teleport(tp); p.sendMessage(color("&aTeleported to &e"+pretty(d.type)+" &aspawner."));}}
            else if(e.getRawSlot()==22)openMain(p,s.key,d);
            else if(e.getRawSlot()==24)openAdminList(p,0);
            return;
        }
        if(s.menu==Menu.MAIN){
            if(e.getRawSlot()==20)openStorage(p,s.key,d,0);
            else if(e.getRawSlot()==22){collectAll(p,d);saveAll();openMain(p,s.key,d);}
            else if(e.getRawSlot()==24){long x=d.xp;d.xp=0;p.giveExp((int)Math.min(Integer.MAX_VALUE,x));p.sendMessage(color("&aCollected &e"+x+" &aXP."));saveAll();openMain(p,s.key,d);}
            else if(e.getRawSlot()==33)sellAll(p,d);
            else if(e.getRawSlot()==49)p.closeInventory();
        }
        else if(s.menu==Menu.STORAGE){if(e.getRawSlot()==48)openMain(p,s.key,d); else if(e.getRawSlot()==45)openStorage(p,s.key,d,s.page-1); else if(e.getRawSlot()==53)openStorage(p,s.key,d,s.page+1); else if(e.getRawSlot()==49)collectAll(p,d); else if(e.getRawSlot()<45){ItemStack clicked=e.getCurrentItem(); if(clicked!=null&&clicked.getType()!=Material.AIR){collectOne(p,d,clicked.getType(),clicked.getAmount()); openStorage(p,s.key,d,s.page);}}}
    }
    @EventHandler public void drag(InventoryDragEvent e){
        if(e.getWhoClicked() instanceof Player p && menus.containsKey(p.getUniqueId())){ e.setCancelled(true); e.setResult(Event.Result.DENY); }
    }
    @EventHandler public void creative(InventoryCreativeEvent e){
        if(e.getWhoClicked() instanceof Player p && menus.containsKey(p.getUniqueId())){ e.setCancelled(true); e.setResult(Event.Result.DENY); }
    }
    @EventHandler public void drop(org.bukkit.event.player.PlayerDropItemEvent e){
        if(menus.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler public void swap(org.bukkit.event.player.PlayerSwapHandItemsEvent e){
        if(menus.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
    private void collectOne(Player p,SpawnerData d,Material m,int amount){long have=d.loot.getOrDefault(m.name(),0L);long take=Math.min(have,amount);ItemStack give=new ItemStack(m,(int)Math.min(Integer.MAX_VALUE,take));Map<Integer,ItemStack> left=p.getInventory().addItem(give);long accepted=take;for(ItemStack v:left.values())accepted-=v.getAmount();if(accepted>0){long n=have-accepted;if(n<=0)d.loot.remove(m.name());else d.loot.put(m.name(),n);} }
    private void collectAll(Player p,SpawnerData d){ for(String name:new ArrayList<>(d.loot.keySet())){Material m=Material.matchMaterial(name);if(m==null)continue;while(d.loot.getOrDefault(name,0L)>0){long take=Math.min(d.loot.get(name),m.getMaxStackSize());Map<Integer,ItemStack> left=p.getInventory().addItem(new ItemStack(m,(int)take));long accepted=take;for(ItemStack x:left.values())accepted-=x.getAmount();if(accepted<=0){d.loot.remove(name);break;}d.loot.put(name,d.loot.get(name)-accepted);break;}}}
    private void sellAll(Player p,SpawnerData d){
        if (!getConfig().getBoolean("economy.enabled", true)) {
            p.sendMessage(color(msg("economy-unavailable")));
            return;
        }
        if (economy == null) {
            p.sendMessage(color(msg("economy-unavailable")));
            return;
        }
        double total = 0.0;
        Map<String,Long> sellable = new LinkedHashMap<>();
        for (Map.Entry<String,Long> entry : d.loot.entrySet()) {
            double price = prices.getDouble("items." + entry.getKey(), 0.0);
            if (price <= 0 || entry.getValue() <= 0) continue;
            sellable.put(entry.getKey(), entry.getValue());
            total += price * entry.getValue();
        }
        if (total <= 0) {
            p.sendMessage(color("&cThere is no sellable loot in this spawner."));
            return;
        }
        EconomyResponse response = economy.depositPlayer(p, total);
        if (!response.transactionSuccess()) {
            p.sendMessage(color(msg("economy-failed").replace("{error}", response.errorMessage == null ? "Unknown economy error" : response.errorMessage)));
            return;
        }
        for (String name : sellable.keySet()) d.loot.remove(name);
        String formatted = economy.format(response.amount);
        p.sendMessage(color(msg("sold").replace("{amount}", formatted)));
    }

    @EventHandler public void close(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;
        Bukkit.getScheduler().runTask(this,()->{
            if(!p.isOnline()) { menus.remove(p.getUniqueId()); return; }
            Inventory top=p.getOpenInventory().getTopInventory();
            String title=p.getOpenInventory().getTitle();
            boolean ours=title!=null && (title.contains("STORAGE") || title.contains("SPAWNER"));
            if(!ours || top.getSize()<=0) menus.remove(p.getUniqueId());
        });
    }
    @Override public boolean onCommand(CommandSender s,Command c,String l,String[] a){
        if(!s.hasPermission("spawnerstack.admin")){s.sendMessage(color(msg("no-permission")));return true;}
        if(a.length==0){s.sendMessage(color("&eUsage: /sv <reload|give|list>"));return true;}
        if(a[0].equalsIgnoreCase("reload")){reloadConfig();prices=YamlConfiguration.loadConfiguration(pricesFile);setupEconomy();s.sendMessage(color("&aSpawnerStack configuration reloaded."));return true;}
        if(a[0].equalsIgnoreCase("list") && s instanceof Player p){openAdminList(p,0);return true;}
        if(a[0].equalsIgnoreCase("give")&&a.length>=3){
            Player p=Bukkit.getPlayerExact(a[1]); EntityType t; try{t=EntityType.valueOf(a[2].toUpperCase(Locale.ROOT));}catch(Exception ex){t=null;}
            if(p==null){s.sendMessage(color("&cPlayer not found."));return true;}
            if(!validSpawnerType(t)){s.sendMessage(color("&cInvalid spawner type."));return true;}
            int n=1; if(a.length>=4)try{n=Math.max(1,Math.min(stackMax(),Integer.parseInt(a[3])));}catch(NumberFormatException ex){s.sendMessage(color("&cAmount must be a number."));return true;}
            SpawnerData d=new SpawnerData(t.name(),n); p.getInventory().addItem(spawnerItem(d)); s.sendMessage(color("&aGiven &e"+pretty(t.name())+" ×"+n+" &ato &f"+p.getName()+"&a.")); return true;
        }
        s.sendMessage(color("&cUnknown admin subcommand. Use /sv reload, /sv give or /sv list.")); return true;
    }

    private void loadActive(){for(String k:data.getConfigurationSection("spawners")!=null?data.getConfigurationSection("spawners").getKeys(false):Set.<String>of()){String path="spawners."+k;String type=data.getString(path+".type");if(type==null)continue;SpawnerData d=new SpawnerData(type,data.getInt(path+".stack",1));d.xp=data.getLong(path+".xp",0);d.lastProduction=data.getLong(path+".lastProduction",System.currentTimeMillis());d.ownerUuid=parseUuid(data.getString(path+".ownerUuid"));d.ownerName=data.getString(path+".ownerName");d.placedAt=data.getLong(path+".placedAt",d.lastProduction);d.playerPlaced=data.getBoolean(path+".playerPlaced",false);if(data.isConfigurationSection(path+".loot"))for(String m:data.getConfigurationSection(path+".loot").getKeys(false))d.loot.put(m,data.getLong(path+".loot."+m,0));Location loc=parseLoc(k);if(loc!=null&&loc.getBlock().getType()==Material.SPAWNER){ try { if(validSpawnerType(EntityType.valueOf(type))) active.put(k,d); } catch(Exception ignored) {} }}}
    private void loadDetached(){if(detachedYaml.getConfigurationSection("items")!=null)for(String token:detachedYaml.getConfigurationSection("items").getKeys(false)){String p="items."+token;SpawnerData d=new SpawnerData(detachedYaml.getString(p+".type","ZOMBIE"),detachedYaml.getInt(p+".stack",1));d.xp=detachedYaml.getLong(p+".xp",0);d.ownerUuid=parseUuid(detachedYaml.getString(p+".ownerUuid"));d.ownerName=detachedYaml.getString(p+".ownerName");d.placedAt=detachedYaml.getLong(p+".placedAt",0);d.playerPlaced=detachedYaml.getBoolean(p+".playerPlaced",true);if(detachedYaml.isConfigurationSection(p+".loot"))for(String m:detachedYaml.getConfigurationSection(p+".loot").getKeys(false))d.loot.put(m,detachedYaml.getLong(p+".loot."+m,0));detached.put(token,new DetachedSpawner(d));}}
    private void saveAll(){data=new YamlConfiguration();for(Map.Entry<String,SpawnerData> e:active.entrySet()){String p="spawners."+e.getKey();SpawnerData d=e.getValue();data.set(p+".type",d.type);data.set(p+".stack",d.stack);data.set(p+".xp",d.xp);data.set(p+".lastProduction",d.lastProduction);data.set(p+".ownerUuid",d.ownerUuid==null?null:d.ownerUuid.toString());data.set(p+".ownerName",d.ownerName);data.set(p+".placedAt",d.placedAt);data.set(p+".playerPlaced",d.playerPlaced);for(Map.Entry<String,Long> x:d.loot.entrySet())data.set(p+".loot."+x.getKey(),x.getValue());}detachedYaml=new YamlConfiguration();for(Map.Entry<String,DetachedSpawner> e:detached.entrySet()){String p="items."+e.getKey();SpawnerData d=e.getValue().data;detachedYaml.set(p+".type",d.type);detachedYaml.set(p+".stack",d.stack);detachedYaml.set(p+".xp",d.xp);detachedYaml.set(p+".ownerUuid",d.ownerUuid==null?null:d.ownerUuid.toString());detachedYaml.set(p+".ownerName",d.ownerName);detachedYaml.set(p+".placedAt",d.placedAt);detachedYaml.set(p+".playerPlaced",d.playerPlaced);for(Map.Entry<String,Long> x:d.loot.entrySet())detachedYaml.set(p+".loot."+x.getKey(),x.getValue());}try{data.save(dataFile);detachedYaml.save(detachedFile);}catch(Exception e){getLogger().warning("Could not save SpawnerStack data: "+e.getMessage());}}
    private UUID parseUuid(String s){try{return s==null?null:UUID.fromString(s);}catch(Exception e){return null;}}
    private SpawnerData copy(SpawnerData x){SpawnerData d=new SpawnerData(x.type,x.stack);d.xp=x.xp;d.lastProduction=x.lastProduction;d.ownerUuid=x.ownerUuid;d.ownerName=x.ownerName;d.placedAt=x.placedAt;d.playerPlaced=x.playerPlaced;d.loot.putAll(x.loot);return d;}
    private enum Menu{MAIN,STORAGE,ADMIN_LIST,ADMIN_DETAIL}
    private record MenuSession(String key,Menu menu,int page){}
    private record DetachedSpawner(SpawnerData data){}
    private static final class SpawnerData{String type;int stack;long xp;long lastProduction;UUID ownerUuid;String ownerName;long placedAt;boolean playerPlaced;Map<String,Long> loot=new LinkedHashMap<>();SpawnerData(String t,int s){type=t;stack=s;lastProduction=System.currentTimeMillis();placedAt=lastProduction;}}
}
