package fi.dy.masa.litematica.selection;

import net.minecraft.core.Direction;

public class BoxSliced extends Box {
    private Direction sliceDirection = Direction.EAST;
    private int sliceStart = 0;
    private int sliceEnd = 1;
    private int sliceCount;

    public Direction getSliceDirection() {
        return sliceDirection;
    }

    /**
     * Returns the inclusive relative start offset from pos1
     * @return
     */
    public int getSliceStart() {
        return sliceStart;
    }

    /**
     * Returns the exclusive relative end offset from pos1
     * @return
     */
    public int getSliceEnd() {
        return sliceEnd;
    }

    public int getSliceCount() {
        return sliceCount;
    }

    public int getMaxSliceLength() {
	    return switch (this.sliceDirection.getAxis()) {
		    case X -> this.getSize().getX();
		    case Y -> this.getSize().getY();
		    case Z -> this.getSize().getZ();
	    };
    }

    public void setSliceDirection(Direction sliceDirection) {
        this.sliceDirection = sliceDirection;
    }

    public void setSliceStart(int sliceStart) {
        this.sliceStart = Math.min(sliceStart, this.getMaxSliceLength() - 1);
    }

    public void setSliceEnd(int sliceEnd) {
        this.sliceEnd = Math.min(sliceEnd, this.getMaxSliceLength());
    }

    public void setSliceCount(int sliceCount) {
        this.sliceCount = sliceCount;
    }
}
