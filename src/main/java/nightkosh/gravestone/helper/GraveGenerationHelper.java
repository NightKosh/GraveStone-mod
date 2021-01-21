package nightkosh.gravestone.helper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import nightkosh.gravestone.api.IGraveStoneHelper;
import nightkosh.gravestone.api.death_handler.ICustomEntityDeathHandler;
import nightkosh.gravestone.api.grave.EnumGraveMaterial;
import nightkosh.gravestone.api.grave.EnumGraveType;
import nightkosh.gravestone.api.grave_items.*;
import nightkosh.gravestone.api.grave_position.IGravePositionHandler;
import nightkosh.gravestone.block.BlockGraveStone;
import nightkosh.gravestone.block.enums.EnumGraves;
import nightkosh.gravestone.config.Config;
import nightkosh.gravestone.core.GSBlock;
import nightkosh.gravestone.core.MobHandler;
import nightkosh.gravestone.core.compatibility.Compatibility;
import nightkosh.gravestone.core.compatibility.CompatibilityWolfArmor;
import nightkosh.gravestone.core.logger.GSLogger;
import nightkosh.gravestone.helper.api.APIGraveGeneration;
import nightkosh.gravestone.inventory.GraveInventory;
import nightkosh.gravestone.tileentity.DeathMessageInfo;
import nightkosh.gravestone.tileentity.TileEntityGraveStone;

import java.util.*;
import java.util.logging.Logger;

/**
 * GraveStone mod
 *
 * @author NightKosh
 * @license Lesser GNU Public License v3 (http://www.gnu.org/licenses/lgpl.html)
 */
public class GraveGenerationHelper implements IGraveStoneHelper {

    public static final IGraveStoneHelper INSTANCE = new GraveGenerationHelper();

    protected static final Random rand = new Random();

    public static ArrayList<Item> swordsList = new ArrayList<>(
            Arrays.asList(
                    Items.WOODEN_SWORD,
                    Items.STONE_SWORD,
                    Items.IRON_SWORD,
                    Items.GOLDEN_SWORD,
                    Items.DIAMOND_SWORD
            )
    );

    public enum EnumGraveTypeByEntity {
        ALL_GRAVES,
        PLAYER_GRAVES,
        VILLAGERS_GRAVES,
        HUMAN_GRAVES,
        PETS_GRAVES,
        DOGS_GRAVES,
        CATS_GRAVES,
        HORSE_GRAVES
    }

    @Override
    public void addSwordToSwordsList(Item sword) {
        if (sword != null) {
            swordsList.add(sword);
        }
    }

    private static final EnumGraveType[] GENERATED_PLAYER_GRAVES_TYPES = {
            EnumGraveType.VERTICAL_PLATE,
            EnumGraveType.CROSS,
            EnumGraveType.OBELISK,
            EnumGraveType.CELTIC_CROSS,
            EnumGraveType.HORIZONTAL_PLATE
    };
    private static final EnumGraveType[] STARVED_PLAYER_GRAVES_TYPES = {
            EnumGraveType.STARVED_CORPSE,
    };
    private static final EnumGraveType[] WITHERED_PLAYER_GRAVES_TYPES = {
            EnumGraveType.WITHERED_CORPSE,
    };
    private static final EnumGraveType[] GENERATED_VILLAGERS_GRAVES_TYPES = {EnumGraveType.VILLAGER_STATUE};
    private static final EnumGraveType[] GENERATED_DOGS_GRAVES_TYPES = {EnumGraveType.DOG_STATUE};
    private static final EnumGraveType[] GENERATED_CAT_GRAVES_TYPES = {EnumGraveType.CAT_STATUE};
    private static final EnumGraveType[] GENERATED_HORSE_GRAVES_TYPES = {EnumGraveType.HORSE_STATUE};
    private static final EnumGraveType[] GENERATED_CREEPER_STATUES_GRAVES_TYPES = {EnumGraveType.CREEPER_STATUE};


    private static void addNonEmptyItems(List<ItemStack> items, NonNullList<ItemStack> itemsToAdd) {
        for (ItemStack stack : itemsToAdd) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
    }

    public static void createPlayerGrave(EntityPlayer player, List<EntityItem> entityItems, DamageSource damageSource, long spawnTime) {
        try {
            if (player.getEntityWorld() != null &&
                    !player.getEntityWorld().getGameRules().getBoolean("keepInventory") &&
                    Config.graveItemsCount > 0 &&
                    !isInRestrictedArea(player)) {
                List<ItemStack> items = new ArrayList<>();

                for (EntityItem entityItem : entityItems) {
                    items.add(entityItem.getItem());
                }

                //GSCompatibilityAntiqueAtlas.placeDeathMarkerAtDeath(player); //TODO !!!!!!!!!!!!
                for (IPlayerItems additionalItems : APIGraveGeneration.PLAYER_ITEMS) {
                    try {
                        List<ItemStack> modItems = additionalItems.addItems(player, damageSource);
                        if (modItems != null && !modItems.isEmpty() && modItems.size() != 0) {
                            items.addAll(modItems);
                        }
                    } catch (Exception e) {
                        GSLogger.logError("Compatibility error occurred in additionalItems.addItems");
                        e.printStackTrace();
                    }
                }

                // remove some items by other mods
                for (IPlayerItems additionalItems : APIGraveGeneration.PLAYER_ITEMS) {
                    try {
                        additionalItems.getItems(player, damageSource, items);
                    } catch (Exception e) {
                        GSLogger.logError("Compatibility error occurred in additionalItems.getItems");
                        e.printStackTrace();
                    }
                }

                if (Config.generateEmptyPlayerGraves || items.size() != 0) {
                    createGrave(player, damageSource, items, EnumGraveTypeByEntity.PLAYER_GRAVES, false, spawnTime);
                }
            } else if (Config.generateEmptyPlayerGraves) {
                createGrave(player, damageSource, null, EnumGraveTypeByEntity.PLAYER_GRAVES, false, spawnTime);
            }
        } catch (Exception e) {
            GSLogger.logError(e.getMessage());
        } finally {
            entityItems.clear();
        }
    }

    public static void createVillagerGrave(EntityVillager villager, DamageSource damageSource) {
        List<ItemStack> items = new ArrayList<>(5);
        for (IVillagerItems additionalItems : APIGraveGeneration.VILLAGER_ITEMS) {
            items.addAll(additionalItems.addItems(villager, damageSource));
        }

        IItemHandler itemHandler = villager.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, villager.getHorizontalFacing());
        if (villager.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, villager.getHorizontalFacing())) {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.extractItem(slot, 100500, false);
                if (stack != null && !stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }

        long spawnTime = MobHandler.getAndRemoveSpawnTime(villager);
        createGrave(villager, damageSource, items, GraveGenerationHelper.EnumGraveTypeByEntity.VILLAGERS_GRAVES, true, spawnTime);
    }

    public static void createDogGrave(EntityWolf dog, DamageSource damageSource) {
        if (dog.isTamed()) {
            long spawnTime = MobHandler.getAndRemoveSpawnTime(dog);
            createGrave(dog, damageSource, getDogsItems(dog, damageSource), EnumGraveTypeByEntity.DOGS_GRAVES, false, spawnTime);
        }
    }

    public static void createCatGrave(EntityOcelot cat, DamageSource damageSource) {
        if (cat.isTamed()) {
            long spawnTime = MobHandler.getAndRemoveSpawnTime(cat);
            createGrave(cat, damageSource, getCatsItems(cat, damageSource), EnumGraveTypeByEntity.CATS_GRAVES, false, spawnTime);
        }
    }

    private static List<ItemStack> getDogsItems(EntityWolf dog, DamageSource damageSource) {
        List<ItemStack> items = new ArrayList<>(5);
        for (IDogItems additionalItems : APIGraveGeneration.DOG_ITEMS) {
            items.addAll(additionalItems.addItems(dog, damageSource));
        }

        if (Compatibility.IS_WOLF_ARMOR_INSTALLED) {
            items.addAll(CompatibilityWolfArmor.getWolfItems(dog));
        }

        return items;
    }

    private static List<ItemStack> getCatsItems(EntityOcelot cat, DamageSource damageSource) {
        List<ItemStack> items = new ArrayList<>(5);
        for (ICatItems additionalItems : APIGraveGeneration.CAT_ITEMS) {
            items.addAll(additionalItems.addItems(cat, damageSource));
        }

        IItemHandler itemHandler = cat.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, cat.getHorizontalFacing());
        if (cat.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, cat.getHorizontalFacing())) {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.extractItem(slot, 100500, false);
                if (stack != null && !stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    public static void createHorseGrave(AbstractHorse horse, DamageSource damageSource) {
        if (horse.isTame()) {
            List<ItemStack> items = new ArrayList<>();
            items.addAll(getHorseItems(horse));

            for (IHorseItems additionalItems : APIGraveGeneration.HORSE_ITEMS) {
                items.addAll(additionalItems.addItems(horse, damageSource));
            }

            long spawnTime = MobHandler.getAndRemoveSpawnTime(horse);
            createGrave(horse, damageSource, items, EnumGraveTypeByEntity.HORSE_GRAVES, false, spawnTime);
        }
    }

    private static List<ItemStack> getHorseItems(AbstractHorse horse) {
        List<ItemStack> items = new ArrayList<>();

        IItemHandler itemHandler = horse.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, horse.getHorizontalFacing());
        if (horse.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, horse.getHorizontalFacing())) {
            for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
                ItemStack stack = itemHandler.extractItem(slot, 100500, false);
                if (stack != null && !stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }

        return items;
    }

    public static void createGrave(Entity entity, DamageSource damageSource, List<ItemStack> items, EnumGraveTypeByEntity graveTypeByEntity, boolean isVillager, long spawnTime) {
        if (isInRestrictedArea(entity)) {
            GSLogger.logInfo("Can't generate " + entity.getName() + "'s grave in restricted area. " + entity.getPosition());
            if (items != null) {
                items.stream().filter(item -> item != null).forEach(item -> {
                    GraveInventory.dropItem(item, entity.getEntityWorld(), entity.getPosition());
                });
            }
        } else {
            int age = (int) (entity.getEntityWorld().getWorldTime() - spawnTime) / 24000;
            BlockPos pos = new BlockPos(entity.posX, Math.ceil(entity.posY), entity.posZ);
            GraveInfoOnDeath graveInfo = getGraveOnDeath(entity.getEntityWorld(), pos, entity, graveTypeByEntity, items, age, damageSource);
            DeathMessageInfo messageInfo = getDeathMessage((EntityLivingBase) entity, damageSource.damageType, isVillager);
            createOnDeath(entity, entity.getEntityWorld(), pos, messageInfo, items, age, graveInfo, damageSource);
        }
    }

    public static void createCustomGrave(Entity entity, LivingDeathEvent event, ICustomEntityDeathHandler customEntityDeathHandler) {
        if (isInRestrictedArea(entity)) {
            GSLogger.logInfo("Can't generate " + entity.getName() + "'s grave in restricted area. " + entity.getPosition());
            if (customEntityDeathHandler.getItems() != null) {
                customEntityDeathHandler.getItems().stream().filter(item -> item != null).forEach(item -> {
                    GraveInventory.dropItem(item, entity.getEntityWorld(), entity.getPosition());
                });
            }
        } else {
            int age = customEntityDeathHandler.getAge();
            GraveInfoOnDeath graveInfo = new GraveInfoOnDeath();
            graveInfo.setGrave(EnumGraves.getByTypeAndMaterial(customEntityDeathHandler.getGraveType(entity, event.getSource()),
                    customEntityDeathHandler.getGraveMaterial(entity, event.getSource())));
            graveInfo.setSword(customEntityDeathHandler.getSword());
            graveInfo.setEnchanted(customEntityDeathHandler.isEnchanted(entity, event.getSource()));
            graveInfo.setMossy(customEntityDeathHandler.isMossy(entity, event.getSource()));

            BlockPos pos = new BlockPos(entity.posX, Math.round(entity.posY), entity.posZ - 1);
            DeathMessageInfo messageInfo = getDeathMessage((EntityLivingBase) entity, event.getSource().damageType, false);
            createOnDeath(entity, entity.getEntityWorld(), pos, messageInfo, customEntityDeathHandler.getItems(), age, graveInfo, event.getSource());
        }
    }

    private static GraveInfoOnDeath getGraveOnDeath(World world, BlockPos pos, Entity entity, EnumGraveTypeByEntity graveTypeByEntity,
                                                    List<ItemStack> items, int age, DamageSource damageSource) {
        GraveInfoOnDeath graveInfo = new GraveInfoOnDeath();
        if (chooseGraveTypeByAgeOrLevel(entity, graveTypeByEntity, age)) {
            EnumGraveMaterial material = getGraveMaterialByAgeOrLevel(entity, age, graveTypeByEntity);
            EnumGraveType[] type;
            if (isExplosionDamage(damageSource)) {
                type = GENERATED_CREEPER_STATUES_GRAVES_TYPES;
            } else {
                type = getDefaultGraveTypes(graveTypeByEntity);
            }

            graveInfo.grave = getGraveType(type, material);
        } else {
            graveInfo.grave = getGraveByDeath(damageSource, graveTypeByEntity, entity, age);
            if (graveInfo.grave == null) {
                if (graveTypeByEntity == EnumGraveTypeByEntity.PLAYER_GRAVES && Config.generateSwordGraves &&
                        world.rand.nextInt(4) == 0 && graveTypeByEntity.equals(EnumGraveTypeByEntity.PLAYER_GRAVES)) {
                    ItemStack sword = getSwordFromInventory(items);
                    if (sword != null) {
                        graveInfo.sword = sword;
                        graveInfo.grave = EnumGraves.SWORD;
                    }
                }

                if (graveInfo.grave == null) {
                    graveInfo.grave = getGraveTypeByBiomes(world, pos, graveTypeByEntity, damageSource);
                }
            }
        }

        graveInfo.setMossy(isMossyGrave(world, pos, graveInfo.grave.getMaterial()));
        graveInfo.setEnchanted(INSTANCE.isMagicDamage(damageSource));

        return graveInfo;
    }

    private static class GraveInfoOnDeath {
        private EnumGraves grave;
        private ItemStack sword;
        private boolean enchanted;
        private boolean mossy;

        public EnumGraves getGrave() {
            return grave;
        }

        public ItemStack getSword() {
            return sword;
        }

        public boolean isEnchanted() {
            return enchanted;
        }

        public boolean isMossy() {
            return mossy;
        }

        public void setGrave(EnumGraves grave) {
            this.grave = grave;
        }

        public void setSword(ItemStack sword) {
            this.sword = sword;
        }

        public void setEnchanted(boolean enchanted) {
            this.enchanted = enchanted;
        }

        public void setMossy(boolean mossy) {
            this.mossy = mossy;
        }
    }

    private static void createOnDeath(Entity entity, World world, BlockPos pos, DeathMessageInfo deathInfo, List<ItemStack> items,
                                      int age, GraveInfoOnDeath graveInfo, DamageSource damageSource) {
        BlockPos newPos = null;
        EnumFacing direction = null;
        World newWorld = null;

        boolean hasCustomLocation = false;
        try {
            for (IGravePositionHandler position : APIGraveGeneration.GRAVE_POSITION_HANDLERS) {
                if (position.condition(world, entity, pos, damageSource)) {
                    newPos = position.gravePosition(world, entity, pos, damageSource);
                    if (newPos != null) {
                        hasCustomLocation = true;
                        direction = position.graveFacing(world, entity, pos, damageSource);
                        newWorld = position.getWorld(world, entity, pos, damageSource);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            GSLogger.logError("Can't get custom position of grave!");
        }

        if (hasCustomLocation) {
            GSLogger.logInfo("Position of grave was changed by other mod");
        } else {
            direction = EnumFacing.getHorizontal(MathHelper.floor((double) (entity.rotationYaw * 4 / 360F) + 0.5) & 3);
            newPos = findPlaceForGrave(world, entity, pos, damageSource);
            if (newPos == null)
            {
                newPos = findPlaceForGrave(world, entity, pos.north(), damageSource);
            }
            if (newPos == null)
            {
                newPos = findPlaceForGrave(world, entity, pos.east(), damageSource);
            }
            if (newPos == null)
            {
                newPos = findPlaceForGrave(world, entity, pos.south(), damageSource);
            }
            if (newPos == null)
            {
                newPos = findPlaceForGrave(world, entity, pos.west(), damageSource);
            }
            newWorld = world;
        }

        BackupsHelper.addBackup(entity, newWorld, newPos, items);

        if (newPos != null) {
            newWorld.setBlockState(newPos, GSBlock.GRAVE_STONE.getDefaultState().withProperty(BlockGraveStone.FACING, direction), 2);
            TileEntityGraveStone tileEntity = (TileEntityGraveStone) newWorld.getTileEntity(newPos);

            if (tileEntity != null) {
                if (graveInfo.getSword() != null) {
                    tileEntity.setSword(graveInfo.getSword());
                }

                tileEntity.getDeathTextComponent().setLocalized();
                tileEntity.getDeathTextComponent().setName(deathInfo.getName());
                tileEntity.getDeathTextComponent().setDeathText(deathInfo.getDeathMessage());
                tileEntity.getDeathTextComponent().setKillerName(deathInfo.getKillerName());
                tileEntity.getInventory().setItems(items);
                tileEntity.setGraveType(graveInfo.getGrave().ordinal());
                tileEntity.setAge(age);
                tileEntity.setEnchanted(graveInfo.isEnchanted());
                tileEntity.setMossy(graveInfo.isMossy());
                if (entity instanceof EntityPlayer) {
                    tileEntity.setOwner(entity.getUniqueID().toString());
                } else if (entity instanceof EntityTameable && ((EntityTameable) entity).isTamed() && ((EntityTameable) entity).getOwner() != null) {
                    tileEntity.setOwner(((EntityTameable) entity).getOwner().getUniqueID().toString());
                }
            }
            GSLogger.logInfo("Create " + deathInfo.getName() + "'s grave at " + newPos);
        } else {
            ItemStack itemStack = new ItemStack(Item.getItemFromBlock(GSBlock.GRAVE_STONE), 1);
            itemStack.setItemDamage(graveInfo.getGrave().ordinal());
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setBoolean("isLocalized", true);
            nbt.setString("name", deathInfo.getName());
            nbt.setString("DeathText", deathInfo.getDeathMessage());
            nbt.setString("KillerName", deathInfo.getKillerNameForTE());
            nbt.setBoolean("Enchanted", graveInfo.isEnchanted());
            nbt.setBoolean("Mossy", graveInfo.isMossy());
            nbt.setInteger("Age", age);

            if (graveInfo.getGrave() == EnumGraves.SWORD) {
                GraveStoneHelper.addSwordInfo(nbt, graveInfo.getSword());
            }

            itemStack.setTagCompound(nbt);
            GraveInventory.dropItem(itemStack, world, pos);

            if (items != null) {
                for (ItemStack item : items) {
                    if (item != null) {
                        GraveInventory.dropItem(item, world, pos);
                    }
                }
            }
            GSLogger.logInfo("Can not create " + deathInfo.getName() + "'s grave at " + pos);
        }
    }

    private static DeathMessageInfo getDeathMessage(EntityLivingBase entity, String damageType, boolean isVillager) {
        EntityLivingBase killer = entity.getAttackingEntity();
        String shortString = "death.attack." + damageType;
        String fullString = shortString + ".player";

        String entityName = entity.getName();
        if (entityName == null) {
            entityName = "entity." + EntityList.getEntityString(entity) + ".name";
        }

        if (killer != null) {
            String killerName;
            if (killer instanceof EntityPlayer) {
                killerName = killer.getDisplayName().getFormattedText();
                if (isVillager) {
                    GSLogger.logInfo("Villager was killed by " + killerName);
                }
            } else {
                killerName = EntityList.getEntityString(killer);
                if (killerName == null) {
                    killerName = "entity.generic.name";
                } else {
                    killerName = "entity." + killerName + ".name";
                }
            }
            if (I18n.canTranslate(fullString)) {
                return new DeathMessageInfo(entityName, fullString, killerName);
            } else {
                return new DeathMessageInfo(entityName, shortString, killerName);
            }
        } else {
            return new DeathMessageInfo(entityName, shortString, null);
        }
    }

    private static boolean isInRestrictedArea(Entity entity) {
        return Config.restrictGraveGenerationInArea.stream().anyMatch((area) -> area.isInArea(entity.getEntityWorld(), entity.getPosition()));
    }

    @Override
    public boolean isMagicDamage(DamageSource damageSource) {
        return DamageSource.MAGIC.equals(damageSource) || damageSource.damageType.toLowerCase().contains("magic");
    }

    @Override
    public boolean isMossyGrave(World world, BlockPos pos, EnumGraveMaterial graveMaterial, EnumGraveType graveType) {
        return isMossyGrave(world, pos, EnumGraves.getByTypeAndMaterial(graveType, graveMaterial).getMaterial());
    }

    public static boolean isMossyGrave(World world, BlockPos pos, EnumGraveMaterial graveMaterial) {
        Set<BiomeDictionary.Type> biomeTypesList = BiomeDictionary.getTypes(world.getBiome(pos));
        return graveMaterial != EnumGraveMaterial.OTHER && (biomeTypesList.contains(BiomeDictionary.Type.JUNGLE) || biomeTypesList.contains(BiomeDictionary.Type.SWAMP));
    }

    public static boolean chooseGraveTypeByAgeOrLevel(Entity entity, EnumGraveTypeByEntity graveTypeByEntity, int age) {
        if (graveTypeByEntity == EnumGraveTypeByEntity.PLAYER_GRAVES) {
            return ((EntityPlayer) entity).experienceLevel >= 15;
        } else {
            return age >= 30;
        }
    }

    public static EnumGraveMaterial getGraveMaterialByAgeOrLevel(Entity entity, int age, EnumGraveTypeByEntity graveTypeByEntity) {
        if (graveTypeByEntity == EnumGraveTypeByEntity.PLAYER_GRAVES) {
            return INSTANCE.getGraveMaterialByLevel(((EntityPlayer) entity).experienceLevel);
        } else {
            return INSTANCE.getGraveMaterialByAge(age);
        }
    }

    @Override
    public EnumGraveMaterial getGraveMaterialByLevel(int level) {
        if (level >= 65) {
            return EnumGraveMaterial.EMERALD;
        } else if (level >= 55) {
            return EnumGraveMaterial.DIAMOND;
        } else if (level >= 45) {
            return EnumGraveMaterial.REDSTONE;
        } else if (level >= 35) {
            return EnumGraveMaterial.GOLD;
        } else if (level >= 25) {
            return EnumGraveMaterial.LAPIS;
        } else {
            return EnumGraveMaterial.IRON;
        }
    }

    @Override
    public EnumGraveMaterial getGraveMaterialByAge(int age) {
        if (age > 180) {
            return EnumGraveMaterial.EMERALD;
        } else if (age > 150) {
            return EnumGraveMaterial.DIAMOND;
        } else if (age > 120) {
            return EnumGraveMaterial.REDSTONE;
        } else if (age > 90) {
            return EnumGraveMaterial.GOLD;
        } else if (age > 60) {
            return EnumGraveMaterial.LAPIS;
        } else {
            return EnumGraveMaterial.IRON;
        }
    }

    protected static EnumGraveType[] getDefaultGraveTypes(EnumGraveTypeByEntity graveTypeByEntity) {
        switch (graveTypeByEntity) {
            case VILLAGERS_GRAVES:
                return GENERATED_VILLAGERS_GRAVES_TYPES;
            case DOGS_GRAVES:
                return GENERATED_DOGS_GRAVES_TYPES;
            case CATS_GRAVES:
                return GENERATED_CAT_GRAVES_TYPES;
            case HORSE_GRAVES:
                return GENERATED_HORSE_GRAVES_TYPES;
            default:
            case PLAYER_GRAVES:
                return GENERATED_PLAYER_GRAVES_TYPES;
        }
    }

    public static EnumGraves getGraveByDeath(DamageSource damageSource, EnumGraveTypeByEntity graveTypeByEntity, Entity entity, int age) {
        EnumGraveType[] graveTypes = null;
        EnumGraveMaterial material;

        if (isFireDamage(damageSource, damageSource.damageType) || isLavaDamage(damageSource, damageSource.damageType)) {
            material = EnumGraveMaterial.OBSIDIAN;
        } else if (graveTypeByEntity == EnumGraveTypeByEntity.PLAYER_GRAVES) {
            //TODO drown
            if (DamageSource.STARVE.equals(damageSource)) {
                graveTypes = STARVED_PLAYER_GRAVES_TYPES;
                material = EnumGraveMaterial.OTHER;
            } else if (DamageSource.WITHER.equals(damageSource)) {
                graveTypes = WITHERED_PLAYER_GRAVES_TYPES;
                material = EnumGraveMaterial.OTHER;
            } else {
                return null;
            }
        } else {
            return null;
        }

        if (graveTypes == null) {
            graveTypes = getDefaultGraveTypes(graveTypeByEntity);
        }

        return getGraveType(graveTypes, material);
    }

    public static boolean isFireDamage(DamageSource damageSource, String damageType) {
        return DamageSource.IN_FIRE.equals(damageSource) || DamageSource.ON_FIRE.equals(damageSource) || isFireDamage(damageType);
    }

    public static boolean isFireDamage(String damageType) {
        return damageType.toLowerCase().contains("nfire");
    }

    public static boolean isLavaDamage(DamageSource damageSource, String damageType) {
        return DamageSource.LAVA.equals(damageSource) || isLavaDamage(damageType);
    }

    public static boolean isLavaDamage(String damageType) {
        return damageType.toLowerCase().contains("lava");
    }

    public static boolean isMagicDamage(String damageText) {
        return damageText.toLowerCase().contains("magic");
    }

    public static boolean isExplosionDamage(DamageSource damageSource) {
        return isBlastDamage(damageSource.damageType) || isFireballDamage(damageSource.damageType);
    }

    public static boolean isBlastDamage(String damageType) {
        return damageType.toLowerCase().contains("explosion");
    }

    public static boolean isFireballDamage(String damageType) {
        return damageType.toLowerCase().contains("fireball");
    }

    public static EnumGraveMaterial[] getGraveMaterialByBiomes(World world, BlockPos pos) {
        Set<BiomeDictionary.Type> biomeTypesList = BiomeDictionary.getTypes(world.getBiome(pos));

        ArrayList<EnumGraveMaterial> materials = new ArrayList<>();
        if (biomeTypesList.contains(BiomeDictionary.Type.SANDY) || biomeTypesList.contains(BiomeDictionary.Type.BEACH)) {
            materials.add(EnumGraveMaterial.SANDSTONE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.JUNGLE) || biomeTypesList.contains(BiomeDictionary.Type.SWAMP)) {
            materials.add(EnumGraveMaterial.STONE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.MOUNTAIN)) {
            materials.add(EnumGraveMaterial.GRANITE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.HILLS)) {
            materials.add(EnumGraveMaterial.ANDESITE);
            materials.add(EnumGraveMaterial.DIORITE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.PLAINS) || biomeTypesList.contains(BiomeDictionary.Type.MUSHROOM)) {
            materials.add(EnumGraveMaterial.STONE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.FOREST)) {
            materials.add(EnumGraveMaterial.WOOD);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.SNOWY)) {
            materials.add(EnumGraveMaterial.ICE);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.NETHER)) {
            materials.add(EnumGraveMaterial.QUARTZ);
        }
        if (biomeTypesList.contains(BiomeDictionary.Type.MESA)) {
            materials.add(EnumGraveMaterial.RED_SANDSTONE);
        }
        // TODO if (biomeTypesList.contains(BiomeDictionary.Type.END)) {} ????????
        if (biomeTypesList.contains(BiomeDictionary.Type.WATER)) {
            materials.add(EnumGraveMaterial.PRIZMARINE);
        }

        if (materials.isEmpty()) {
            materials.add(EnumGraveMaterial.STONE);
        }

        EnumGraveMaterial[] materialsArray = new EnumGraveMaterial[materials.size()];
        return materials.toArray(materialsArray);
    }

    public static EnumGraves getGraveTypeByBiomes(World world, BlockPos pos, EnumGraveTypeByEntity graveTypeByEntity, DamageSource damageSource) {
        EnumGraveMaterial[] materialsArray = getGraveMaterialByBiomes(world, pos);

        EnumGraveType[] type;
        if (damageSource != null && isExplosionDamage(damageSource)) {
            type = GENERATED_CREEPER_STATUES_GRAVES_TYPES;
        } else {
            type = getDefaultGraveTypes(graveTypeByEntity);
        }
        return getGraveType(type, materialsArray);
    }

    private static BlockPos findPlaceForGrave(World world, Entity entity, BlockPos pos, DamageSource damageSource) {

        BlockPos fixPos = new BlockPos(pos.getX(), Math.min(250, Math.max(pos.getY(), 6)), pos.getZ());

        if ((world.isOutsideBuildHeight(pos) || pos.getY() < world.getHeight())) {
            BlockPos groundPos = trySetGround(world, fixPos);
            if (groundPos != null) return groundPos;
            GSLogger.logInfo("Can't find position for grave on death in the void!" + pos);
        }

        int newY = getGround(world, fixPos);
        if (newY == -1) {
            GSLogger.logInfo("Can't find position for grave on death at" + pos);
            return null;
        }
        BlockPos newPos = new BlockPos(fixPos.getX(), newY, fixPos.getZ());
        BlockPos groundPos = trySetGround(world, newPos);
        if (groundPos != null) return groundPos;
        GSLogger.logInfo("Can't find position for grave on death at" + newPos);
        return null;
    }

    private static BlockPos trySetGround(World world, BlockPos pos) {
        if (isReplaceable(world, pos)) {
            world.setBlockState(pos, Blocks.GRASS.getDefaultState());
            GSLogger.logInfo("Grave set grass at " + pos.toString());
            return pos.up();
        } else return null;
    }

    private static boolean isReplaceable(World world, BlockPos pos) {
        return isReplaceableBlock(world, pos) && isReplaceableBlock(world, pos.up());
    }

    private static boolean isReplaceableBlock(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return world.isAirBlock(pos) || state.getBlock().getMaterial(state).isLiquid() || state.getBlock().getMaterial(state).isReplaceable();
    }

    public static int getGround(World world, BlockPos pos) {
        int orginY = pos.getY();
        while (pos.getY() < 250) {
            if (isReplaceable(world, pos)) {
                return pos.getY();
            } else {
                pos = pos.up();
            }
        }
        pos = new BlockPos(pos.getX(), orginY, pos.getZ());
        while (pos.getY() >= 6) {
            if (isReplaceable(world, pos)) {
                return pos.getY();
            } else {
                pos = pos.down();
            }
        }
        return -1;
    }

    /*
    public static boolean canGenerateGraveAtCoordinates(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        IBlockState stateDown = world.getBlockState(pos.down());
        return stateDown.getBlock().getMaterial(stateDown).isSolid() && stateDown.getBlock().isFullCube(stateDown) &&
                (world.isAirBlock(pos) || state.getBlock().getMaterial(state).isLiquid() || state.getBlock().getMaterial(state).isReplaceable());
    }*/

    protected static EnumGraves getGraveType(EnumGraveType[] graveTypes, EnumGraveMaterial... materials) {
        EnumGraveType graveType = graveTypes[rand.nextInt(graveTypes.length)];
        EnumGraveMaterial material = materials[rand.nextInt(materials.length)];

        return EnumGraves.getByTypeAndMaterial(graveType, material);
    }

    private static ItemStack getSwordFromInventory(List<ItemStack> items) {
        if (items != null) {
            for (ItemStack stack : items) {
                if (stack != null && swordsList.contains(stack.getItem())) {
                    ItemStack sword = stack.copy();
                    items.remove(stack);
                    return sword;
                }
            }
        }

        return null;
    }
}
