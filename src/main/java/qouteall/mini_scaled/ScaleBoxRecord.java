package qouteall.mini_scaled;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class ScaleBoxRecord extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleBoxRecord.class);
    
    private List<Entry> entries = new ArrayList<>();
    
    @Nullable
    private Int2ObjectOpenHashMap<Entry> cache;
    
    public ScaleBoxRecord() {
        super();
    }
    
    public static ScaleBoxRecord get() {
        ServerLevel overworld = MiscHelper.getServer().overworld();
        
        return overworld.getDataStorage().computeIfAbsent(
            (nbt) -> {
                ScaleBoxRecord record = new ScaleBoxRecord();
                record.readFromNbt(nbt);
                return record;
            },
            () -> {
                Helper.log("Scale box record initialized ");
                return new ScaleBoxRecord();
            },
            "scale_box_record"
        );
    }
    
    @Nullable
    public Entry getEntryById(int boxId) {
        if (cache == null) {
            cache = new Int2ObjectOpenHashMap<>();
            for (Entry entry : entries) {
                cache.put(entry.id, entry);
            }
        }
        
        return cache.get(boxId);
    }
    
    @Nullable
    public Entry getEntryByPredicate(Predicate<Entry> predicate) {
        return entries.stream().filter(predicate).findFirst().orElse(null);
    }
    
    public void addEntry(Entry entry) {
        entries.add(entry);
        cache = null;
    }
    
    // does not allow removing entry
    
    public int allocateId() {
        return entries.stream().mapToInt(e -> e.id).max().orElse(0) + 1;
    }
    
    private void readFromNbt(CompoundTag compoundTag) {
        ListTag list = compoundTag.getList("entries", compoundTag.getId());
        entries = list.stream().map(tag -> {
            Entry entry = new Entry();
            entry.readFromNbt(((CompoundTag) tag));
            return entry;
        }).collect(Collectors.toList());
        cache = null;
    }
    
    private void writeToNbt(CompoundTag compoundTag) {
        ListTag listTag = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            entry.writeToNbt(tag);
            listTag.add(tag);
        }
        compoundTag.put("entries", listTag);
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
        
        IntBox getInnerUnitBox(BlockPos outerOffset) {
            return IntBox.fromBasePointAndSize(
                this.innerBoxPos.offset(outerOffset.multiply(this.scale)),
                new BlockPos(this.scale, this.scale, this.scale)
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
            innerBoxPos = Helper.getVec3i(tag, "innerBoxPos");
            scale = tag.getInt("size"); // the old name is "size"
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
        }
        
        void writeToNbt(CompoundTag tag) {
            tag.putInt("id", id);
            Helper.putVec3i(tag, "innerBoxPos", innerBoxPos);
            tag.putInt("size", scale); // the old name is "size"
            tag.putString("color", color.getName());
            tag.putUUID("ownerId", ownerId);
            tag.putString("ownerNameCache", ownerNameCache);
            if (currentEntranceDim != null) {
                tag.putString("currentEntranceDim", currentEntranceDim.location().getPath());
            }
            Helper.putVec3i(tag, "currentEntrancePos", currentEntrancePos);
            Helper.putVec3i(tag, "currentEntranceSize", currentEntranceSize);
            tag.putInt("generation", generation);
            if (entranceRotation != null) {
                tag.putInt("entranceRotation", entranceRotation.ordinal());
            }
            tag.putBoolean("teleportChangesScale", teleportChangesScale);
            tag.putBoolean("teleportChangesGravity", teleportChangesGravity);
        }
    }
}
