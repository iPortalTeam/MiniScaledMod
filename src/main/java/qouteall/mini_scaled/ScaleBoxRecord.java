package qouteall.mini_scaled;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.mini_scaled.util.AARotation;
import qouteall.mini_scaled.util.MSUtil;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ScaleBoxRecord extends PersistentState {
    private List<Entry> entries = new ArrayList<>();
    
    @Nullable
    private Int2ObjectOpenHashMap<Entry> cache;
    
    public ScaleBoxRecord() {
        super();
    }
    
    public static ScaleBoxRecord get() {
        ServerWorld overworld = MiscHelper.getServer().getOverworld();
        
        return overworld.getPersistentStateManager().getOrCreate(
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
    
    private void readFromNbt(NbtCompound compoundTag) {
        NbtList list = compoundTag.getList("entries", compoundTag.getType());
        entries = list.stream().map(tag -> {
            Entry entry = new Entry();
            entry.readFromNbt(((NbtCompound) tag));
            return entry;
        }).collect(Collectors.toList());
        cache = null;
    }
    
    private void writeToNbt(NbtCompound compoundTag) {
        NbtList listTag = new NbtList();
        for (Entry entry : entries) {
            NbtCompound tag = new NbtCompound();
            entry.writeToNbt(tag);
            listTag.add(tag);
        }
        compoundTag.put("entries", listTag);
    }
    
    public void fromTag(NbtCompound tag) {
        readFromNbt(tag);
    }
    
    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
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
        public RegistryKey<World> currentEntranceDim; // null means the scale box is not being put
        public BlockPos currentEntrancePos;
        public BlockPos currentEntranceSize; // Note: before rotation
        public int generation;
        @Nullable
        public AARotation entranceRotation; // the rotation from inner scale box to outer entrance
        
        public Entry() {
        
        }
        
        public IntBox getOuterAreaBox() {
            AARotation rot = getEntranceRotation();
            BlockPos actualEntranceSize = rot.transform(currentEntranceSize);
            return MSUtil.getBoxByPosAndSignedSize(currentEntrancePos, actualEntranceSize);
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
                this.innerBoxPos.add(outerOffset.multiply(this.scale)),
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
        
        void readFromNbt(NbtCompound tag) {
            id = tag.getInt("id");
            innerBoxPos = Helper.getVec3i(tag, "innerBoxPos");
            scale = tag.getInt("size"); // the old name is "size"
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            ownerId = tag.getUuid("ownerId");
            ownerNameCache = tag.getString("ownerNameCache");
            if (tag.contains("currentEntranceDim")) {
                currentEntranceDim = RegistryKey.of(
                    RegistryKeys.WORLD,
                    new Identifier(tag.getString("currentEntranceDim"))
                );
            }
            else {
                currentEntranceDim = null;
            }
            currentEntrancePos = Helper.getVec3i(tag, "currentEntrancePos");
            
            if (tag.contains("currentEntranceSizeX")) {
                currentEntranceSize = Helper.getVec3i(tag, "currentEntranceSize");
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
        }
        
        void writeToNbt(NbtCompound tag) {
            tag.putInt("id", id);
            Helper.putVec3i(tag, "innerBoxPos", innerBoxPos);
            tag.putInt("size", scale);
            tag.putString("color", color.getName());
            tag.putUuid("ownerId", ownerId);
            tag.putString("ownerNameCache", ownerNameCache);
            if (currentEntranceDim != null) {
                tag.putString("currentEntranceDim", currentEntranceDim.getValue().getPath());
            }
            Helper.putVec3i(tag, "currentEntrancePos", currentEntrancePos);
            Helper.putVec3i(tag, "currentEntranceSize", currentEntranceSize);
            tag.putInt("generation", generation);
            if (entranceRotation != null) {
                tag.putInt("entranceRotation", entranceRotation.ordinal());
            }
        }
    }
}
