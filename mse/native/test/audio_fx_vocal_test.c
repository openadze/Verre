/* Host test for audio_fx_set_vocal:  cc -O2 -I.. ../audio_fx.c audio_fx_vocal_test.c -o vocal_test -lm -lpthread && ./vocal_test */
#include "audio_fx.h"

#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

static double rms_tail(const int16_t *s, int n) /* left channel, second half */
{
    double a = 0;
    int i;
    for (i = n / 2; i < n; i++)
        a += (double)s[i * 2] * s[i * 2];
    return sqrt(a / (n - n / 2));
}

static double run(float level, float hz, int centre)
{
    const int n = 48000;
    int16_t *b = (int16_t *)malloc((size_t)n * 4);
    AudioFx *fx = audio_fx_create();
    double r;
    int i;
    audio_fx_set_rate(fx, 48000);
    audio_fx_set_vocal(fx, level);
    for (i = 0; i < n; i++) {
        double v = 8000 * sin(2 * M_PI * hz * i / 48000);
        b[i * 2] = (int16_t)v;
        b[i * 2 + 1] = (int16_t)(centre ? v : -v);
    }
    audio_fx_color(fx, b, n, 2);
    r = rms_tail(b, n);
    audio_fx_destroy(fx);
    free(b);
    return r;
}

int main(void)
{
    int bad = 0;
    if (run(1.f, 800, 1) < 5500) bad |= 1;  /* off: untouched */
    if (run(0.f, 800, 1) > 200) bad |= 2;   /* centre vocal band removed */
    if (run(0.f, 60, 1) < 5400) bad |= 4;   /* bass kept */
    if (run(0.f, 8000, 1) < 5400) bad |= 8; /* treble kept */
    if (run(0.f, 800, 0) < 5500) bad |= 16; /* side (out of phase) kept */
    printf(bad ? "FAIL %d\n" : "ok\n", bad);
    return bad ? 1 : 0;
}
