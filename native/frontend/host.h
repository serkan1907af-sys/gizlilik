/* Minimal libretro host: loads one core (.so), runs it frame by frame.
 * Everything here must be called from ONE thread (the emulation thread). */
#ifndef HOST_H
#define HOST_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Joypad bit positions == RETRO_DEVICE_ID_JOYPAD_* values. */

int    host_load_core(const char *core_path, const char *system_dir, const char *save_dir);
/* controller_device: 0 = leave default, otherwise passed to retro_set_controller_port_device(0, ..) */
int    host_load_game(const char *game_path, unsigned controller_device);
void   host_set_input(uint32_t buttons, int lx, int ly, int rx, int ry);
void   host_run(void);
void   host_unload(void);

int    host_width(void);
int    host_height(void);
float  host_aspect(void);
double host_fps(void);
double host_sample_rate(void);
int    host_frame_count(void);
int    host_shutdown_requested(void);
void   host_tap_key(unsigned retrok);

/* Copies the last video frame as RGBA bytes (R,G,B,A in memory order).
 * dst_stride_px is the row length of dst in pixels. */
void   host_copy_rgba(uint32_t *dst, int dst_stride_px);

/* Moves queued stereo audio into dst. Returns number of stereo frames. */
int    host_take_audio(int16_t *dst, int max_frames);

const char *host_last_error(void);

#ifdef __cplusplus
}
#endif

#endif
