## BrightWay v1.9 — the compass waits to be sure, then keeps up

**The needle no longer trails the phone.** It was animated with a 250 ms tween that got a new
target on every sensor sample. A tween starts from a standstill each time it is retargeted, so at
50 samples a second the needle only ever travelled the flat opening of the easing curve before
being handed another one: it lagged most of a second behind a turn and never caught up while the
turn continued. It is a spring now, which carries its velocity across retargets, and the jitter
that the tween was really there to hide is taken out where it belongs — a 90 ms filter on the
heading itself, averaged as a vector rather than as a number, because 359 and 1 are two degrees
apart and their mean is not 180. The rotation vector is read at 50 Hz instead of 16 for the same
reason: nothing to smooth means nothing arrives early.

**A figure 8 before the arrow.** The magnetometer's hard-iron calibration goes stale in a pocket
and the HAL keeps reporting high confidence on it, which is how this compass opened pointing at a
wall and then held the wrong direction steadily enough to be believed. Android has no API to ask
for a recalibration; the wave is the only way in. So the face is withheld until the phone has
actually been waved — watched for on the gyroscope, two axes reversing at a rate no footstep or
pocket reaches, seven radians of turning inside one window — *and* the sensor says it is sure
afterwards. The wave without the confirmation is as wrong as before, just quietly.

An 8 to copy is drawn where the compass will be, and fills in as the gesture is recognised. The
wave is remembered for as long as the app is running, so walking between the nav compass and the
standalone one does not ask again, and it is forgotten the moment the sensor admits its
calibration is gone.

**Walking skips all of it.** A GPS course is a track over the ground and owes the magnetometer
nothing, so a fix with speed on it still shows the compass immediately, as it always did. A phone
with no gyroscope is never asked for a gesture nobody can see.

