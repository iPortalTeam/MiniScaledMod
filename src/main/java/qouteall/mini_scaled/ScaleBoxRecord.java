package qouteall.mini_scaled;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScaleBoxRecord extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleBoxRecord.class);
    
    private final Int2ObjectAVLTreeMap<Entry> byId = new Int2ObjectAVLTreeMap<>();
    
    private final Map<UUID, List<Entry>> byOwner = new Object2ObjectOpenHashMap<>();
    
    private final BitSet regionIdUsage = new BitSet();
    
    private int maxId = 0;
    
    public ScaleBoxRecord() {
        super();
    }
    
    public static ScaleBoxRecord get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        
        return overworld.getDataStorage().computeIfAbsent(
            new Factory<ScaleBoxRecord>(
                () -> {
                    Helper.log("Scale box record initialized ");
                    return new ScaleBoxRecord();
                },
                (nbt) -> {
                    ScaleBoxRecord record = new ScaleBoxRecord();
                    record.readFromNbt(nbt);
                    return record;
                },
                null
            ),
            "scale_box_record"
        );
    }
    
    @Nullable
    public Entry getEntryById(int boxId) {
        return byId.get(boxId);
    }
    
    public List<Entry> getEntriesByOwner(UUID uuid) {
        return Collections.unmodifiableList(byOwner.getOrDefault(uuid, Collections.emptyList()));
    }
    
    public Collection<Entry> getAllEntries() {
        return byId.values();
    }
    
    public void addEntry(Entry entry) {
        byId.put(entry.id, entry);
        byOwner.computeIfAbsent(entry.ownerId, k -> new ObjectArrayList<>()).add(entry);
        regionIdUsage.set(entry.regionId, true);
    }
    
    public boolean removeEntry(int id) {
        Entry entry = byId.remove(id);
        if (entry != null) {
            List<Entry> entriesForOwner = byOwner.get(entry.ownerId);
            if (entriesForOwner == null) {
                LOGGER.error("Cannot find entries for owner {} {}", entry, byOwner);
            }
            else {
                boolean removed = entriesForOwner.remove(entry);
                if (!removed) {
                    LOGGER.error("Cannot find entry for owner {} {}", entry, byOwner);
                }
            }
            
            int regionId = entry.regionId;
            
            boolean oldRegionBit = regionIdUsage.get(regionId);
            if (!oldRegionBit) {
                LOGGER.error("Removing an entry while region id bit not set {}", entry);
            }
            regionIdUsage.set(regionId, false);
            
            return true;
        }
        
        return false;
    }
    
    public int allocateId() {
        maxId++;
        return maxId;
    }
    
    public int reserveRegionId() {
        // region id allocation start from 1
        int regionId = regionIdUsage.nextClearBit(1);
        regionIdUsage.set(regionId, true);
        return regionId;
    }
    
    public void clearRegionId(int regionId) {
        regionIdUsage.clear(regionId);
    }
    
    private void readFromNbt(CompoundTag compoundTag) {
        byId.clear();
        byOwner.clear();
        maxId = 0;
        regionIdUsage.clear();
        
        ListTag list = compoundTag.getList("entries", compoundTag.getId());
        
        for (Tag tag : list) {
            if (tag instanceof CompoundTag c) {
                Entry entry = Entry.fromTag(c);
                addEntry(entry);
            }
        }
        
        maxId = compoundTag.getInt("maxId");
        
        // old versions of MiniScaled does not put maxId tag. update it
        int actualMaxId = byId.isEmpty() ? 0 : byId.lastIntKey();
        maxId = Math.max(maxId, actualMaxId);
    }
    
    private void writeToNbt(CompoundTag compoundTag) {
        ListTag listTag = new ListTag();
        for (Entry entry : byId.values()) {
            listTag.add(entry.toTag());
        }
        compoundTag.put("entries", listTag);
        compoundTag.putInt("maxId", maxId);
    }
    
    public void fromTag(CompoundTag tag) {
        readFromNbt(tag);
    }
    
    @Override
    public CompoundTag save(CompoundTag tag) {
        writeToNbt(tag);
        return tag;
    }
    
    public static class Entry {
        public int id;
        public int regionId;
        public BlockPos innerBoxPos;
        public int scale;
        public DyeColor color;
        public UUID ownerId;
        public String ownerNameCache;
        @Nullable
        public ResourceKey<Level> currentEntranceDim; // null means the scale box is not being put
        public BlockPos currentEntrancePos;
        public BlockPos currentEntranceSize; // Note: before rotation
        public int generation;
        @Nullable
        public AARotation entranceRotation; // the rotation from inner scale box to outer entrance
        public boolean teleportChangesScale = false;
        public boolean teleportChangesGravity = false;
        public boolean accessControl = false;
        
        // this is used for deleting invalid portal when server restarts during unwrapping animation
        public @Nullable Long scheduledUnwrapTime;
        
        public Entry() {
        
        }
        
        public IntBox getOuterAreaBox() {
            AARotation rot = getEntranceRotation();
            BlockPos actualEntranceSize = rot.transform(currentEntranceSize);
            return IntBox.getBoxByPosAndSignedSize(currentEntrancePos, actualEntranceSize);
        }
        
        public IntBox getInnerAreaBox() {
            return IntBox.fromBasePointAndSize(
                this.innerBoxPos,
                new BlockPos(
                    this.scale * currentEntranceSize.getX(),
                    this.scale * currentEntranceSize.getY(),
                    this.scale * currentEntranceSize.getZ()
                )
            );
        }
        
        public IntBox getInnerUnitBox(BlockPos outerOffset) {
            return IntBox.fromBasePointAndSize(
                this.innerBoxPos.offset(outerOffset.multiply(this.scale)),
                new BlockPos(this.scale, this.scale, this.scale)
            );
        }
        
        public BlockPos unitRegionOffsetToBlockOffset(BlockPos unitRegionOffset) {
            return unitRegionOffset.multiply(this.scale);
        }
        
        public BlockPos blockOffsetToUnitRegionOffset(BlockPos blockOffset) {
            return new BlockPos(
                Math.floorDiv(blockOffset.getX(), this.scale),
                Math.floorDiv(blockOffset.getY(), this.scale),
                Math.floorDiv(blockOffset.getZ(), this.scale)
            );
        }
        
        public IntBox getInnerAreaLocalBox() {
            return IntBox.fromBasePointAndSize(
                BlockPos.ZERO,
                new BlockPos(
                    this.scale * currentEntranceSize.getX(),
                    this.scale * currentEntranceSize.getY(),
                    this.scale * currentEntranceSize.getZ()
                )
            );
        }
        
        public AARotation getEntranceRotation() {
            if (entranceRotation == null) {
                return AARotation.IDENTITY;
            }
            return entranceRotation;
        }
        
        public AARotation getRotationToInner() {
            return getEntranceRotation().getInverse();
        }
        
        void readFromNbt(CompoundTag tag) {
            id = tag.getInt("id");
            
            if (tag.contains("regionId")) {
                regionId = tag.getInt("regionId");
            }
            else {
                regionId = id; // for old data, use id as region id
            }
            
            if (regionId < 0) {
                LOGGER.error("Invalid region id {}", regionId);
                regionId = 0;
            }
            
            innerBoxPos = Helper.getVec3i(tag, "innerBoxPos");
            
            if (tag.contains("scale")) {
                scale = tag.getInt("scale");
            }
            else {
                scale = tag.getInt("size"); // the old name is "size"
            }
            
            if (scale <= 0) {
                LOGGER.error("Invalid scale {} {}", scale, tag);
                scale = 4;
            }
            
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            ownerId = tag.getUUID("ownerId");
            ownerNameCache = tag.getString("ownerNameCache");
            if (tag.contains("currentEntranceDim")) {
                currentEntranceDim = ResourceKey.create(
                    Registries.DIMENSION,
                    new ResourceLocation(tag.getString("currentEntranceDim"))
                );
            }
            else {
                currentEntranceDim = null;
            }
            currentEntrancePos = Helper.getVec3i(tag, "currentEntrancePos");
            
            if (tag.contains("currentEntranceSizeX")) {
                currentEntranceSize = Helper.getVec3i(tag, "currentEntranceSize");
                if (currentEntranceSize.getX() == 0 || currentEntranceSize.getY() == 0 || currentEntranceSize.getZ() == 0) {
                    LOGGER.error("Invalid entrance size in {}", tag);
                    currentEntranceSize = new BlockPos(1, 1, 1);
                }
            }
            else {
                currentEntranceSize = new BlockPos(1, 1, 1);
            }
            
            generation = tag.getInt("generation");
            
            if (tag.contains("entranceRotation")) {
                entranceRotation = AARotation.values()[tag.getInt("entranceRotation")];
            }
            else {
                entranceRotation = null;
            }
            
            if (tag.contains("teleportChangesScale")) {
                teleportChangesScale = tag.getBoolean("teleportChangesScale");
            }
            else {
                teleportChangesScale = false;
            }
            
            if (tag.contains("teleportChangesGravity")) {
                teleportChangesGravity = tag.getBoolean("teleportChangesGravity");
            }
            else {
                teleportChangesGravity = false;
            }
            
            if (tag.contains("accessControl")) {
                accessControl = tag.getBoolean("accessControl");
            }
            else {
                accessControl = false;
            }
            
            if (tag.contains("scheduledUnwrapTime")) {
                scheduledUnwrapTime = tag.getLong("scheduledUnwrapTime");
            }
            else {
                scheduledUnwrapTime = null;
            }
            
            if (regionId > 100000) {
                LOGGER.error("Region id too large {}. Something may be wrong.", this);
            }
        }
        
        void writeToNbt(CompoundTag tag) {
            tag.putInt("id", id);
            tag.putInt("regionId", regionId);
            Helper.putVec3i(tag, "innerBoxPos", innerBoxPos);
            tag.putInt("scale", scale);
            tag.putString("color", color.getName());
            tag.putUUID("ownerId", ownerId);
            tag.putString("ownerNameCache", ownerNameCache);
            if (currentEntranceDim != null) {
                tag.putString("currentEntranceDim", currentEntranceDim.location().toString());
            }
            Helper.putVec3i(tag, "currentEntrancePos", currentEntrancePos);
            Helper.putVec3i(tag, "currentEntranceSize", currentEntranceSize);
            tag.putInt("generation", generation);
            if (entranceRotation != null) {
                tag.putInt("entranceRotation", entranceRotation.ordinal());
            }
            tag.putBoolean("teleportChangesScale", teleportChangesScale);
            tag.putBoolean("teleportChangesGravity", teleportChangesGravity);
            tag.putBoolean("accessControl", accessControl);
            if (scheduledUnwrapTime != null) {
                tag.putLong("scheduledUnwrapTime", scheduledUnwrapTime);
            }
        }
        
        public static Entry fromTag(CompoundTag tag) {
            Entry entry = new Entry();
            entry.readFromNbt(tag);
            return entry;
        }
        
        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            writeToNbt(tag);
            return tag;
        }
        
        public ChunkLoader createChunkLoader(int extraRenderDistance) {
            IntBox innerAreaBox = getInnerAreaBox();
            BlockPos size = innerAreaBox.getSize();
            return new ChunkLoader(
                new DimensionalChunkPos(
                    VoidDimension.KEY,
                    new ChunkPos(innerAreaBox.getCenter())
                ),
                Math.max(size.getX(), size.getZ()) / (16 * 2) + extraRenderDistance
            );
        }
        
        @Override
        public String toString() {
            return "Entry{" +
                "id=" + id +
                ", regionId=" + regionId +
                ", innerBoxPos=" + innerBoxPos +
                ", scale=" + scale +
                ", color=" + color +
                ", ownerId=" + ownerId +
                ", ownerNameCache='" + ownerNameCache + '\'' +
                ", currentEntranceDim=" + currentEntranceDim +
                ", currentEntrancePos=" + currentEntrancePos +
                ", currentEntranceSize=" + currentEntranceSize +
                ", generation=" + generation +
                ", entranceRotation=" + entranceRotation +
                ", teleportChangesScale=" + teleportChangesScale +
                ", teleportChangesGravity=" + teleportChangesGravity +
                ", accessControl=" + accessControl +
                '}';
        }
    }
}
