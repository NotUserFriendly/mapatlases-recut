package notuserfriendly.mapatlasesrecut.utils;

/**
 * Duck interface on {@code LevelChunk}: the game time at which a block in this chunk last
 * changed. Used to decide whether a map covering the chunk is worth rescanning.
 */
public interface ChunkChangeStamp {

    long mapatlasesrecut$lastBlockChange();

    void mapatlasesrecut$markBlockChanged(long gameTime);
}
