/* Minimal libretro host for console cores: loads one core (.so) and runs it frame by frame.
 * Everything here must be called from ONE thread (the emulation thread). */
#ifndef HOST_H
#define HOST_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Joypad bit positions == RETRO_DEVICE_ID_JOYPAD_* values. */

/* Core options (retro_variable). Call BEFORE host_load_core. */
void   host_set_option(const char *key, const char *value);

int    host_load_core(const char *core_path, const char *system_dir, const char *save_dir);
int    host_load_game(const char *game_path);
void   host_set_input(uint32_t buttons);
void   host_run(void);
void   host_reset(void);
void   host_unload(void);

int    host_width(void);
int    host_height(void);
float  host_aspect(void);
double host_fps(void);
double host_sample_rate(void);
int    host_frame_count(void);

/* Battery saves: files "<base>.srm" and "<base>.rtc". */
void   host_load_saves(const char *base);
int    host_flush_saves(void);   /* returns how many files were (re)written */

/* Save states. */
int    host_state_size(void);
int    host_save_state(const char *path);
int    host_load_state(const char *path);

/* Rewind: snapshots every 'every' frames, using at most 'mb' megabytes. */
void   host_rewind_enable(int mb, int every);
int    host_rewind_step(void);   /* 1 = went back, 0 = nothing left */
int    host_rewind_count(void);

/* Cheats (format depends on the core). */
void   host_cheat_reset(void);
void   host_cheat_set(unsigned index, int enabled, const char *code);

/* Copies the last video frame as RGBA bytes (R,G,B,A in memory order). */
void   host_copy_rgba(uint32_t *dst, int dst_stride_px);

/* Moves queued stereo audio into dst. Returns number of stereo frames. */
int    host_take_audio(int16_t *dst, int max_frames);

const char *host_last_error(void);

#ifdef __cplusplus
}
#endif

#endif
