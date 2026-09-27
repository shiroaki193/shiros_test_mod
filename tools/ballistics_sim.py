"""Creeper mortar ballistics simulator.

Mirrors vanilla ThrowableProjectile#tick() order (same as Ballistics.java):
  vel.y -= gravity; vel *= drag; pos += vel

Usage: python tools/ballistics_sim.py [pitch_deg] [range ...]
"""
import math
import sys

GRAVITY = 0.05
DRAG = 0.99
PITCH_DEG = 60.0
MAX_TICKS = 600


def simulate(speed, pitch, target_dy=0.0, gravity=GRAVITY, drag=DRAG):
    """Return (range, apex, ticks, impact_angle_deg) when the shell comes down to target_dy."""
    x = y = apex = 0.0
    vx, vy = speed * math.cos(pitch), speed * math.sin(pitch)
    for tick in range(1, MAX_TICKS + 1):
        vx *= drag
        vy = (vy - gravity) * drag
        x += vx
        y += vy
        apex = max(apex, y)
        if vy < 0 and y <= target_dy:
            return x, apex, tick, math.degrees(math.atan2(-vy, vx))
    return None


def solve_speed(distance, pitch, target_dy=0.0, lo=0.1, hi=8.0, iterations=50):
    """Binary-search the launch speed that lands at `distance` for a fixed pitch."""
    for _ in range(iterations):
        mid = (lo + hi) / 2
        result = simulate(mid, pitch, target_dy)
        if result is None or result[0] < distance:
            lo = mid
        else:
            hi = mid
    return hi


def main():
    pitch_deg = float(sys.argv[1]) if len(sys.argv) > 1 else PITCH_DEG
    ranges = [float(r) for r in sys.argv[2:]] or [24, 40, 80, 120]
    pitch = math.radians(pitch_deg)
    print(f"gravity={GRAVITY} drag={DRAG} pitch={pitch_deg}")
    print("range | speed | apex  | ticks (s)    | impact")
    for distance in ranges:
        speed = solve_speed(distance, pitch)
        _, apex, ticks, impact = simulate(speed, pitch)
        print(f"{distance:5.0f} | {speed:5.2f} | {apex:5.1f} | {ticks:4d} ({ticks / 20:4.1f}s) | {impact:5.1f}°")


if __name__ == "__main__":
    main()
