package com.stutteranalyzer.client;

import com.stutteranalyzer.classifier.FreezeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public class AlertHoverText {

    public static Component build(FreezeCategory category, long ms, String visibleMsg) {
        String hoverText = hoverFor(category, ms);
        Component hover = Component.literal(hoverText);
        return Component.literal(visibleMsg)
            .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static String hoverFor(FreezeCategory cat, long ms) {
        return switch (cat) {
            case CLIENT_RENDER_STUTTER ->
                "Client rendering stutter, " + ms + "ms.\n\n"
                + "The rendering pipeline had a short pause.\n\n"
                + "Common causes:\n"
                + "- chunk mesh rebuild\n"
                + "- shader compilation\n"
                + "- entity or block entity rendering spike\n"
                + "- texture or resource loading\n\n"
                + "What to try:\n"
                + "- try Sodium/Embeddium or Entity Culling\n"
                + "- reduce render distance\n"
                + "- use /sa optimize suggest";

            case SERVER_TICK_SPIKE ->
                "Server tick spike, " + ms + "ms.\n\n"
                + "The server took too long to process one tick.\n\n"
                + "Common causes:\n"
                + "- too many entities or block entities\n"
                + "- heavy mod logic running every tick\n"
                + "- world save or chunk loading during tick\n\n"
                + "What to try:\n"
                + "- check entity counts with /sa status\n"
                + "- try Lithium or server optimization mods";

            case CHUNK_GENERATION ->
                "Chunk generation, " + ms + "ms.\n\n"
                + "The game is generating new terrain, which is slow.\n\n"
                + "Common causes:\n"
                + "- exploring new areas\n"
                + "- worldgen mods adding complex structures\n\n"
                + "What to try:\n"
                + "- pre-generate the world if possible\n"
                + "- try C2ME or Noisium to speed up worldgen";

            case CHUNK_LOADING ->
                "Chunk loading, " + ms + "ms.\n\n"
                + "Chunks are being loaded from disk or streamed in.\n\n"
                + "Common causes:\n"
                + "- fast movement or teleporting\n"
                + "- slow disk or many mods adding chunk data\n\n"
                + "What to try:\n"
                + "- try C2ME for async chunk loading\n"
                + "- check disk speed";

            case CHUNK_RENDER_REBUILD ->
                "Chunk render rebuild, " + ms + "ms.\n\n"
                + "The renderer is rebuilding visible geometry for nearby chunks.\n\n"
                + "Common causes:\n"
                + "- lots of block updates nearby\n"
                + "- explosions or large build operations\n\n"
                + "What to try:\n"
                + "- use Sodium/Embeddium for faster chunk meshing\n"
                + "- reduce render distance";

            case RESOURCE_RELOAD ->
                "Resource reload, " + ms + "ms.\n\n"
                + "Game assets (textures, sounds, models) are being reloaded.\n\n"
                + "Common causes:\n"
                + "- pressing F3+T or changing resource packs\n"
                + "- some mods trigger reloads automatically\n\n"
                + "What to try:\n"
                + "- avoid unnecessary reloads\n"
                + "- ImmediatelyFast can speed up rendering after reloads";

            case SHADER_OR_RESOURCE_PACK_RELOAD ->
                "Shader/resource pack reload, " + ms + "ms.\n\n"
                + "Shaders or a resource pack is being loaded or reloaded.\n\n"
                + "Common causes:\n"
                + "- enabling/disabling shaders\n"
                + "- switching resource packs at game startup\n\n"
                + "What to try:\n"
                + "- normal for shader packs\n"
                + "- try Iris/Oculus for more optimized shader loading";

            case MEMORY_PRESSURE ->
                "Memory pressure, " + ms + "ms.\n\n"
                + "The game is running low on RAM.\n\n"
                + "Common causes:\n"
                + "- too many mods or high-res textures\n"
                + "- not enough RAM allocated\n"
                + "- memory leak in a mod\n\n"
                + "What to try:\n"
                + "- allocate more RAM (4-8GB for modpacks)\n"
                + "- try FerriteCore to reduce memory usage\n"
                + "- try MemoryLeakFix";

            case GARBAGE_COLLECTION ->
                "Java garbage collection, " + ms + "ms.\n\n"
                + "The Java VM is cleaning up unused memory.\n\n"
                + "Common causes:\n"
                + "- normal GC activity\n"
                + "- too many short-lived objects being created\n"
                + "- insufficient heap size\n\n"
                + "What to try:\n"
                + "- allocate more RAM\n"
                + "- try G1GC or ZGC JVM arguments\n"
                + "- FerriteCore helps reduce object creation";

            case ENTITY_TICK_OVERLOAD ->
                "Entity tick overload, " + ms + "ms.\n\n"
                + "Too many entities are being processed each tick.\n\n"
                + "Common causes:\n"
                + "- mob farms or large animal pens\n"
                + "- entity spawning bugs from mods\n\n"
                + "What to try:\n"
                + "- find and remove excess mobs\n"
                + "- use Entity Culling to skip invisible entities";

            case BLOCK_ENTITY_TICK_OVERLOAD ->
                "Block entity tick overload, " + ms + "ms.\n\n"
                + "Too many machines or tile entities are ticking.\n\n"
                + "Common causes:\n"
                + "- many machines/pipes from tech mods\n"
                + "- automated farms with many block entities\n\n"
                + "What to try:\n"
                + "- reduce active machines in loaded chunks\n"
                + "- try Enhanced Block Entities (Fabric)";

            case WORLD_SAVE ->
                "World save, " + ms + "ms.\n\n"
                + "The game is writing world data to disk.\n\n"
                + "Common causes:\n"
                + "- auto-save trigger or manual /save-all\n"
                + "- slow disk write speed\n\n"
                + "What to try:\n"
                + "- normal, just wait\n"
                + "- faster SSD reduces save pauses\n"
                + "- ModernFix reduces save overhead";

            case DATAPACK_OR_COMMAND_FUNCTION ->
                "Datapack/command function, " + ms + "ms.\n\n"
                + "A datapack function or scheduled command took too long.\n\n"
                + "Common causes:\n"
                + "- heavy datapack with many commands per tick\n"
                + "- looping functions in a datapack\n\n"
                + "What to try:\n"
                + "- check your datapacks for expensive commands\n"
                + "- reduce function tick rate";

            case NETWORK_OR_PACKET_SPIKE ->
                "Network/packet spike, " + ms + "ms.\n\n"
                + "A large packet or network delay caused a stutter.\n\n"
                + "Common causes:\n"
                + "- large chunk data being sent\n"
                + "- many players joining or interacting\n"
                + "- poor network connection\n\n"
                + "What to try:\n"
                + "- check your connection\n"
                + "- try Packet Fixer or Connectivity mods (Forge)";

            case PLAYER_JOIN ->
                "Player join, " + ms + "ms.\n\n"
                + "A player just joined, causing a processing spike.\n\n"
                + "Common causes:\n"
                + "- loading player data and sending world state\n"
                + "- worse with many mods or large inventories\n\n"
                + "What to try:\n"
                + "- normal event, not a real problem";

            case PLAYER_TELEPORT_OR_DIMENSION_CHANGE ->
                "Teleport/dimension change, " + ms + "ms.\n\n"
                + "A player teleported or changed dimension.\n\n"
                + "Common causes:\n"
                + "- loading a new area or transferring player state\n"
                + "- worse with many mods or far distances\n\n"
                + "What to try:\n"
                + "- normal, just wait\n"
                + "- ModernFix speeds up dimension transitions";

            case LIGHTING_ENGINE_SPIKE ->
                "Lighting engine spike, " + ms + "ms.\n\n"
                + "The lighting engine is calculating light updates.\n\n"
                + "Common causes:\n"
                + "- placing/breaking many light sources\n"
                + "- chunk loading in dark areas\n\n"
                + "What to try:\n"
                + "- reduce light-emitting block spam\n"
                + "- Starlight may help if available for your version";

            case WORLDGEN_NOISE ->
                "World generation noise, " + ms + "ms.\n\n"
                + "Procedural noise for terrain generation is slow.\n\n"
                + "Common causes:\n"
                + "- complex terrain from biome or worldgen mods\n"
                + "- first-time generation in new areas\n\n"
                + "What to try:\n"
                + "- try Noisium to speed up noise calculations\n"
                + "- pre-generate your world";

            case LOD_GENERATION ->
                "LOD generation, " + ms + "ms.\n\n"
                + "A distance rendering mod is generating far-away terrain.\n\n"
                + "Common causes:\n"
                + "- Distant Horizons or similar LOD mod active\n"
                + "- first-time LOD generation for the area\n\n"
                + "What to try:\n"
                + "- normal for LOD mods\n"
                + "- let it finish generating";

            case LOD_CACHE_IO ->
                "LOD cache I/O, " + ms + "ms.\n\n"
                + "A distance rendering mod is reading/writing its cache.\n\n"
                + "Common causes:\n"
                + "- Distant Horizons loading cached terrain\n"
                + "- slow disk or first load\n\n"
                + "What to try:\n"
                + "- normal for LOD mods with large worlds\n"
                + "- faster SSD helps";

            case BACKGROUND_THROTTLING ->
                "Background throttling, " + ms + "ms.\n\n"
                + "The game is running in background mode and limited by the OS.\n\n"
                + "Common causes:\n"
                + "- Minecraft window is not focused\n"
                + "- Dynamic FPS limiting background framerate\n\n"
                + "What to try:\n"
                + "- switch back to the Minecraft window\n"
                + "- this is expected and harmless";

            case MOD_INITIALIZATION_OR_LATE_LOADING ->
                "Mod late loading, " + ms + "ms.\n\n"
                + "A mod is loading resources or setting up after game start.\n\n"
                + "Common causes:\n"
                + "- mod loading textures or data lazily on first use\n"
                + "- JIT compilation of mod code\n\n"
                + "What to try:\n"
                + "- normal at startup, improves after warmup\n"
                + "- LazyDFU defers some heavy startup loading";

            case KNOWN_CRASH_PATTERN ->
                "Known crash pattern, " + ms + "ms.\n\n"
                + "A stutter matching a known bug pattern was detected.\n\n"
                + "Common causes:\n"
                + "- a specific mod bug was recognized\n\n"
                + "What to try:\n"
                + "- run /sa preview for details\n"
                + "- run /sa submit to send the report";

            case EMERGENCY_GUARD_TRIGGERED ->
                "Emergency guard triggered, " + ms + "ms.\n\n"
                + "Stutter Analyzer's emergency guard kicked in to prevent worse lag.\n\n"
                + "Common causes:\n"
                + "- extreme lag was detected\n\n"
                + "What to try:\n"
                + "- run /sa preview for details\n"
                + "- check latest.log for what triggered it";

            case WORLD_JOIN_LOAD_SPIKE ->
                "World join loading, " + ms + "ms.\n\n"
                + "A large spike while joining or loading the world.\n\n"
                + "Common causes:\n"
                + "- many mods loading data at join time\n"
                + "- large world or player inventory\n\n"
                + "What to try:\n"
                + "- normal when joining with many mods\n"
                + "- ModernFix speeds up game loading";

            case WORLD_JOIN_RENDER_STALL ->
                "World join render stall, " + ms + "ms.\n\n"
                + "The renderer stalled while setting up after joining.\n\n"
                + "Common causes:\n"
                + "- initial chunk rendering on join\n"
                + "- resource pack or shader loading\n\n"
                + "What to try:\n"
                + "- normal, especially on first join\n"
                + "- ImmediatelyFast reduces render startup time";

            case RESOURCE_RELOAD_STALL ->
                "Resource reload stall, " + ms + "ms.\n\n"
                + "A resource reload caused a noticeable pause.\n\n"
                + "Common causes:\n"
                + "- many resource packs or high-res textures\n"
                + "- mod registering too many assets\n"
                + "- pressing F3+T\n\n"
                + "What to try:\n"
                + "- reduce resource pack count\n"
                + "- ImmediatelyFast improves reload speed";

            case CHUNK_LOADING_HITCH ->
                "Chunk loading hitch, " + ms + "ms.\n\n"
                + "A short pause caused by chunk loading.\n\n"
                + "Common causes:\n"
                + "- normal chunk streaming while moving\n"
                + "- disk I/O for saved chunks\n\n"
                + "What to try:\n"
                + "- C2ME parallelizes chunk loading\n"
                + "- faster SSD reduces load hitches";

            case MODPACK_STARTUP_STALL ->
                "Modpack startup stall, " + ms + "ms.\n\n"
                + "A stall detected at or just after game startup.\n\n"
                + "Common causes:\n"
                + "- many mods loading simultaneously\n"
                + "- JIT warmup on first use\n\n"
                + "What to try:\n"
                + "- normal at startup, usually improves\n"
                + "- LazyDFU defers heavy startup loading";

            case PERIODIC_SCHEDULED_MICRO_HITCH ->
                "Scheduled micro-hitch, " + ms + "ms.\n\n"
                + "A recurring small pause, likely from a scheduled task.\n\n"
                + "Common causes:\n"
                + "- mod running a background task on a timer\n"
                + "- Java GC on a schedule\n"
                + "- auto-save or cache flush\n\n"
                + "What to try:\n"
                + "- usually harmless if short\n"
                + "- use /sa alerts medium if minor alerts are too noisy";

            case PERIODIC_MINOR_MICRO_HITCH ->
                "Periodic micro-hitch, " + ms + "ms.\n\n"
                + "A repeating tiny stutter detected.\n\n"
                + "Common causes:\n"
                + "- JVM garbage collector running periodically\n"
                + "- mod tick running on a timer\n\n"
                + "What to try:\n"
                + "- use /sa alerts medium if minor alerts are noisy\n"
                + "- watch if it keeps repeating";

            case UNCLASSIFIED_MICRO_HITCH ->
                "Minor micro-hitch, " + ms + "ms.\n\n"
                + "A very short pause. You may notice it as a tiny hitch.\n\n"
                + "Common causes:\n"
                + "- chunk loading\n"
                + "- background Java or GC work\n"
                + "- small rendering spike\n"
                + "- modpack background tasks\n\n"
                + "What to try:\n"
                + "- ignore occasional minor hitches\n"
                + "- use /sa alerts medium if minor alerts are too noisy";

            case UNCLASSIFIED_FRAME_HITCH ->
                "Medium frame hitch, " + ms + "ms.\n\n"
                + "A noticeable short pause, usually client-side.\n\n"
                + "Common causes:\n"
                + "- rendering spike\n"
                + "- chunk rebuild\n"
                + "- entity rendering\n"
                + "- resource loading\n\n"
                + "What to try:\n"
                + "- check if it happens while moving or loading chunks\n"
                + "- try renderer or culling optimization mods\n"
                + "- run /sa preview after a bigger freeze";

            case UNKNOWN_EXTREME_FREEZE ->
                "Extreme freeze, " + ms + "ms.\n\n"
                + "The game paused for more than a second.\n\n"
                + "Common causes:\n"
                + "- world loading or resource reload\n"
                + "- heavy mod initialization\n"
                + "- GC or memory pressure\n"
                + "- severe server tick spike or disk stall\n\n"
                + "What to try:\n"
                + "- run /sa preview\n"
                + "- run /sa submit\n"
                + "- check latest.log";

            case UNKNOWN_FREEZE ->
                "Freeze, " + ms + "ms.\n\n"
                + "The game paused for a noticeable duration.\n\n"
                + "Common causes:\n"
                + "- chunk generation or loading\n"
                + "- resource reload or heavy mod logic\n"
                + "- server tick spike or disk/memory pressure\n\n"
                + "What to try:\n"
                + "- run /sa preview\n"
                + "- run /sa submit if useful";
        };
    }
}
