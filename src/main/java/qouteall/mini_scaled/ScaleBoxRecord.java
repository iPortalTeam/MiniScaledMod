package qouteall.mini_scaled;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.IntBox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScaleBoxRecord extends PersistentState {
    public List<Entry> entries = new ArrayList<>();
    
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
    
    //nullable
    public static Entry getEntryById(int boxId) {
        ScaleBoxRecord scaleBoxRecord = get();
        Entry entry = scaleBoxRecord.entries.stream()
            .filter(e -> e.id == boxId)
            .findFirst().orElse(null);
        return entry;
    }
    
    private void readFromNbt(NbtCompound compoundTag) {
        NbtList list = compoundTag.getList("entries", compoundTag.getType());
        entries = list.stream().map(tag -> {
            Entry entry = new Entry();
            entry.readFromNbt(((NbtCompound) tag));
            return entry;
        }).collect(Collectors.toList());
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
        public int size;//16 or 32
        public DyeColor color;
        public UUID ownerId;
        public String ownerNameCache;
        public RegistryKey<World> currentEntranceDim;
        public BlockPos currentEntrancePos;
        public int generation;
        
        public Entry() {
        
        }
    
        IntBox getAreaBox() {
            return IntBox.getBoxByBasePointAndSize(
                new BlockPos(this.size, this.size, this.size),
                this.innerBoxPos
            );
        }
    
        void readFromNbt(NbtCompound tag) {
            id = tag.getInt("id");
            innerBoxPos = Helper.getVec3i(tag, "innerBoxPos");
            size = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            ownerId = tag.getUuid("ownerId");
            ownerNameCache = tag.getString("ownerNameCache");
            currentEntranceDim = RegistryKey.of(
                Registry.WORLD_KEY,
                new Identifier(tag.getString("currentEntranceDim"))
            );
            currentEntrancePos = Helper.getVec3i(tag, "currentEntrancePos");
            generation = tag.getInt("generation");
            
        }
        
        void writeToNbt(NbtCompound tag) {
            tag.putInt("id", id);
            Helper.putVec3i(tag, "innerBoxPos", innerBoxPos);
            tag.putInt("size", size);
            tag.putString("color", color.getName());
            tag.putUuid("ownerId", ownerId);
            tag.putString("ownerNameCache", ownerNameCache);
            tag.putString("currentEntranceDim", currentEntranceDim.getValue().getPath());
            Helper.putVec3i(tag, "currentEntrancePos", currentEntrancePos);
            tag.putInt("generation", generation);
        }
    }
}
