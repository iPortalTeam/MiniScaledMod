package qouteall.mini_scaled;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScaleBoxRecord extends PersistentState {
    public List<Entry> entries = new ArrayList<>();
    
    public ScaleBoxRecord() {
        super("scale_box_record");
    }
    
    public static ScaleBoxRecord get() {
        ServerWorld overworld = McHelper.getServer().getOverworld();
        
        return overworld.getPersistentStateManager().getOrCreate(
            ScaleBoxRecord::new, "scale_box_record"
        );
    }
    
    public void readFromNbt(CompoundTag compoundTag) {
        ListTag list = compoundTag.getList("entries", compoundTag.getType());
        entries = list.stream().map(tag -> {
            Entry entry = new Entry();
            entry.readFromNbt(((CompoundTag) tag));
            return entry;
        }).collect(Collectors.toList());
    }
    
    public void writeToNbt(CompoundTag compoundTag) {
        ListTag listTag = new ListTag();
        for (Entry entry : entries) {
            CompoundTag tag = new CompoundTag();
            entry.writeToNbt(tag);
            listTag.add(tag);
        }
        compoundTag.put("entries", listTag);
    }
    
    @Override
    public void fromTag(CompoundTag tag) {
        readFromNbt(tag);
    }
    
    @Override
    public CompoundTag toTag(CompoundTag tag) {
        writeToNbt(tag);
        return tag;
    }
    
    public static class Entry {
        public int id;
        public BlockPos boxPos;
        public int size;//16 or 32
        public DyeColor color;
        public UUID ownerId;
        public RegistryKey<World> currentEntranceDim;
        public BlockPos currentEntrancePos;
        
        public Entry() {
        
        }
        
        void readFromNbt(CompoundTag tag) {
            id = tag.getInt("id");
            boxPos = Helper.getVec3i(tag, "boxPos");
            size = tag.getInt("size");
            color = DyeColor.byName(tag.getString("color"), DyeColor.BLACK);
            ownerId = tag.getUuid("ownerId");
            currentEntranceDim = RegistryKey.of(
                Registry.DIMENSION,
                new Identifier(tag.getString("currentEntranceDim"))
            );
            currentEntrancePos = Helper.getVec3i(tag, "currentEntrancePos");
            
        }
        
        void writeToNbt(CompoundTag tag) {
            tag.putInt("id", id);
            Helper.putVec3i(tag, "boxPos", boxPos);
            tag.putInt("size", size);
            tag.putString("color", color.getName());
            tag.putUuid("ownerId", ownerId);
            tag.putString("currentEntranceDim", currentEntranceDim.getValue().getPath());
            Helper.putVec3i(tag, "currentEntrancePos", currentEntrancePos);
        }
    }
}
