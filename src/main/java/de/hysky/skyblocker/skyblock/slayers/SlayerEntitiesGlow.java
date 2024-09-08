package de.hysky.skyblocker.skyblock.slayers;

import de.hysky.skyblocker.annotations.Init;
import de.hysky.skyblocker.utils.SlayerUtils;
import de.hysky.skyblocker.utils.render.RenderHelper;
import de.hysky.skyblocker.utils.scheduler.Scheduler;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.hysky.skyblocker.utils.SlayerUtils.SLAYER_MINI_NAMES;
import static de.hysky.skyblocker.utils.SlayerUtils.SLAYER_MOB_TYPE;

public class SlayerEntitiesGlow {

    @Init
    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((ignore, ignore2, ignore3) -> clearGlow());
    }

    private static final Set<UUID> MOBS_TO_GLOW = new HashSet<>();

    /**
     * ARMORSTAND_TO_MOBS_TO_GLOW tracks if an armor stand already has an associated mob entity. This is used for trying to dedupe glows,
     * where an armor stand has detected multiple candidates as its associated mob entity -  in a vain attempt to reduce the amount of false positives
     */
    private static final ConcurrentHashMap<UUID, UUID> ARMORSTAND_TO_MOBS_TO_GLOW = new ConcurrentHashMap<>();

    public static boolean shouldGlow(UUID entityUUID) {
        return MOBS_TO_GLOW.contains(entityUUID);
    }

    public static boolean isSlayer(LivingEntity e) {
        return Slayer.getInstance().isInSlayerFight() && SlayerUtils.getEntityArmorStands(e, 2.5f).stream().anyMatch(entity ->
                entity.getDisplayName().getString().contains(MinecraftClient.getInstance().getSession().getUsername()));
    }

    public static boolean isSlayerMiniMob(LivingEntity entity) {
        if (entity.getCustomName() == null) return false;
        String entityName = entity.getCustomName().getString();
        return SLAYER_MINI_NAMES.keySet().stream().anyMatch(slayerMobName -> entityName.contains(slayerMobName) && Slayer.getInstance().isBossType(SLAYER_MINI_NAMES.get(slayerMobName)));
    }

    public static Box getSlayerMobBoundingBox(LivingEntity entity) {
        return switch (Slayer.getInstance().getBossType()) {
            case SlayerUtils.REVENANT -> new Box(entity.getX() - 0.4, entity.getY() - 0.1, entity.getZ() - 0.4, entity.getX() + 0.4, entity.getY() - 2.2, entity.getZ() + 0.4);
            case SlayerUtils.TARA -> new Box(entity.getX() - 0.9, entity.getY() - 0.2, entity.getZ() - 0.9, entity.getX() + 0.9, entity.getY() - 1.2, entity.getZ() + 0.9);
            case SlayerUtils.VOIDGLOOM -> new Box(entity.getX() - 0.4, entity.getY() - 0.2, entity.getZ() - 0.4, entity.getX() + 0.4, entity.getY() - 3, entity.getZ() + 0.4);
            case SlayerUtils.SVEN -> new Box(entity.getX() - 0.5, entity.getY() - 0.1, entity.getZ() - 0.5, entity.getX() + 0.5, entity.getY() - 1, entity.getZ() + 0.5);
            default -> entity.getBoundingBox();
        };
    }

    /**
     * <p> Finds the closest matching MobEntity for the armorStand using entityClass and armorStand age difference to filter
     * out impossible candidates, returning the closest mob of those remaining in the search box by block distance </p>
     *
     * @param entityClass the mob type of the Slayer (i.e. ZombieEntity.class)
     * @param armorStand  the entity that contains the display name of the Slayer (mini)boss
     */
    private static MobEntity findClosestMobEntity(Class<? extends MobEntity> entityClass, ArmorStandEntity armorStand) {
        List<MobEntity> mobEntities = armorStand.getWorld().getEntitiesByClass(entityClass, armorStand.getDimensions(null)
                        .getBoxAt(armorStand.getPos()).expand(0.3f, 1.5f, 0.3f), entity -> !entity.isDead())
                .stream()
                .filter(SlayerEntitiesGlow::isValidSlayerMob)
                .sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(armorStand)))
                .collect(Collectors.toList());

        return switch (mobEntities.size()) {
            case 0 -> null;
            case 1 -> mobEntities.getFirst();
            default -> mobEntities.stream()
                    .filter(entity -> entity.age > armorStand.age - 4 && entity.age < armorStand.age + 4)
                    .findFirst()
                    .orElse(mobEntities.getFirst());
        };
    }

    /**
     * Use this func to add checks to prevent accidental highlights
     * i.e. Cavespider extends spider and thus will highlight the broodfather's head pet instead and
     */
    private static boolean isValidSlayerMob(MobEntity entity) {
        return !(entity instanceof CaveSpiderEntity) && !(entity.isBaby());
    }

    /**
     * <p> Adds the Entity UUID to the Hashset of Slayer Mobs to glow </p>
     *
     * @param armorStand the entity that contains the display name of the Slayer (mini)boss
     */
    public static void setSlayerMobGlow(ArmorStandEntity armorStand) {
        String slayerType = Slayer.getInstance().getBossType();
        Class<? extends MobEntity> entityClass = SLAYER_MOB_TYPE.get(slayerType);
        if (entityClass != null) {
            MobEntity closestEntity = findClosestMobEntity(entityClass, armorStand);
            if (closestEntity != null) {
               UUID uuid =  ARMORSTAND_TO_MOBS_TO_GLOW.putIfAbsent(armorStand.getUuid(), closestEntity.getUuid());
                if (uuid != null && closestEntity.getUuid() != uuid && closestEntity.age < 80) {
                    Scheduler.INSTANCE.schedule(() -> recalculateMobGlow(armorStand, entityClass, uuid), 30, true);
                }
                MOBS_TO_GLOW.add(closestEntity.getUuid());
            }
        }
    }

    /**
     *  This method attempts self-correct by finding the true slayer mob if there's 2 candidates
     * @param armorStand the armor stand we know to be a slayer mob
     * @param entityClass the java class of the entity we know the armor stand to belong to
     * @param oldUUID the uuid of the first detected slayer mob
     */
    private static void recalculateMobGlow(ArmorStandEntity armorStand, Class<? extends MobEntity> entityClass, UUID oldUUID) {
        MobEntity entity = findClosestMobEntity(entityClass, armorStand);
        if (entity.getUuid() != oldUUID) {
            RenderHelper.runOnRenderThread(() -> {
                MOBS_TO_GLOW.add(entity.getUuid());
                MOBS_TO_GLOW.remove(ARMORSTAND_TO_MOBS_TO_GLOW.put(armorStand.getUuid(), entity.getUuid()));
            });

        }
    }

    public static void onEntityDeath(@Nullable Entity entity) {
        if (entity != null && entity.getUuid() != null) {
            MOBS_TO_GLOW.remove(entity.getUuid());
        }
    }

    public static void cleanupArmorstand(@Nullable ArmorStandEntity entity) {
        if (entity != null && entity.getUuid() != null) {
           ARMORSTAND_TO_MOBS_TO_GLOW.remove(entity.getUuid());
        }
    }

    private static void clearGlow() {
        MOBS_TO_GLOW.clear();
        ARMORSTAND_TO_MOBS_TO_GLOW.clear();
    }

}
