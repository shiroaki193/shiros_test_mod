package com.shiroaki193.mod.ballistics;

/**
 * Pure-math model of the creeper shell's flight. It mirrors vanilla
 * {@code ThrowableProjectile#tick()} exactly (gravity, then drag, then move), so anything
 * predicted here (AI aiming, snow golem lead, landing crosshair) matches the real entity.
 *
 * <p>Kept free of Minecraft classes so it can be unit tested without a game instance.
 */
public final class Ballistics {
    public static final double GRAVITY = 0.05;
    public static final double DRAG = 0.99;
    public static final double DEFAULT_PITCH_DEG = 60.0;
    public static final double MAX_RANGE = 120.0;
    /** Upper bound for the speed search; well above what {@link #MAX_RANGE} needs at 60 degrees. */
    public static final double MAX_SPEED = 6.0;
    private static final int MAX_FLIGHT_TICKS = 600;

    private Ballistics() {
    }

    /**
     * How an object moves through the air each tick.
     *
     * @param moveFirst {@code false} for throwable projectiles (forces, then move);
     *                  {@code true} for mobs, whose {@code LivingEntity#travelInAir} moves first
     */
    public record Model(double gravity, double horizontalDrag, double verticalDrag, boolean moveFirst) {
        /** Creeper shells: vanilla {@code ThrowableProjectile} with our gravity. */
        public static final Model SHELL = new Model(GRAVITY, DRAG, DRAG, false);
        /** Airborne mobs (thrown cats and creepers) with no movement input. */
        public static final Model MOB = new Model(0.08, 0.91, 0.98, true);
    }

    /** Position and velocity, in blocks and blocks/tick. */
    public record State(double x, double y, double z, double vx, double vy, double vz) {
        /** Advances one game tick using the vanilla throwable projectile order. */
        public State step(double gravity, double drag) {
            double nvx = vx * drag;
            double nvy = (vy - gravity) * drag;
            double nvz = vz * drag;
            return new State(x + nvx, y + nvy, z + nvz, nvx, nvy, nvz);
        }

        public State step() {
            return step(GRAVITY, DRAG);
        }

        public State step(Model m) {
            if (!m.moveFirst()) {
                double nvx = vx * m.horizontalDrag();
                double nvy = (vy - m.gravity()) * m.verticalDrag();
                double nvz = vz * m.horizontalDrag();
                return new State(x + nvx, y + nvy, z + nvz, nvx, nvy, nvz);
            }
            return new State(x + vx, y + vy, z + vz,
                    vx * m.horizontalDrag(), (vy - m.gravity()) * m.verticalDrag(), vz * m.horizontalDrag());
        }

        public State advance(int ticks) {
            State s = this;
            for (int i = 0; i < ticks; i++) {
                s = s.step();
            }
            return s;
        }
    }

    /** Where and when a shell comes down through a given height. */
    public record Landing(double x, double y, double z, double ticks, double apexY, double impactAngleDeg) {
        public double horizontalDistanceFrom(double ox, double oz) {
            double dx = x - ox;
            double dz = z - oz;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * Simulates until the shell descends through {@code groundY}. The crossing point is
     * interpolated inside the tick so short and long shots are equally precise.
     *
     * @return the landing, or {@code null} if the shell never comes down through that height
     */
    public static Landing landing(State start, double groundY) {
        return landing(start, groundY, Model.SHELL);
    }

    public static Landing landing(State start, double groundY, Model model) {
        State prev = start;
        double apex = start.y();
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            State next = prev.step(model);
            apex = Math.max(apex, next.y());
            if (next.vy() < 0 && next.y() <= groundY) {
                if (prev.y() < groundY) {
                    // Never got above the ground height this tick (e.g. target above the apex).
                    return null;
                }
                double f = (prev.y() - groundY) / (prev.y() - next.y());
                double horizontal = Math.sqrt(next.vx() * next.vx() + next.vz() * next.vz());
                return new Landing(
                        prev.x() + (next.x() - prev.x()) * f,
                        groundY,
                        prev.z() + (next.z() - prev.z()) * f,
                        tick - 1 + f,
                        apex,
                        Math.toDegrees(Math.atan2(-next.vy(), horizontal)));
            }
            prev = next;
        }
        return null;
    }

    /**
     * Finds the launch speed that lands a shell {@code distance} blocks away at a height
     * difference of {@code dy}, for a fixed pitch.
     *
     * @return the speed in blocks/tick, or {@link Double#NaN} if it needs more than {@link #MAX_SPEED}
     */
    public static double solveSpeed(double distance, double dy, double pitchRad) {
        return solveSpeed(distance, dy, pitchRad, Model.SHELL, MAX_SPEED);
    }

    public static double solveSpeed(double distance, double dy, double pitchRad, Model model, double maxSpeed) {
        if (landedDistance(maxSpeed, dy, pitchRad, model) < distance) {
            return Double.NaN;
        }
        double lo = 0.0;
        double hi = maxSpeed;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            if (landedDistance(mid, dy, pitchRad, model) < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    private static double landedDistance(double speed, double dy, double pitchRad, Model model) {
        State s = new State(0, 0, 0, speed * Math.cos(pitchRad), speed * Math.sin(pitchRad), 0);
        Landing l = landing(s, dy, model);
        return l == null ? -1 : l.x();
    }

    /**
     * Launch velocity from {@code (fx, fy, fz)} to {@code (tx, ty, tz)} along the default pitch.
     *
     * @return {vx, vy, vz}, or {@code null} if the target is beyond {@link #MAX_RANGE} or unreachable
     */
    public static double[] launchVelocity(double fx, double fy, double fz, double tx, double ty, double tz) {
        return launchVelocity(fx, fy, fz, tx, ty, tz, Math.toRadians(DEFAULT_PITCH_DEG), MAX_RANGE);
    }

    public static double[] launchVelocity(double fx, double fy, double fz, double tx, double ty, double tz,
                                          double pitchRad, double maxRange) {
        return launchVelocity(fx, fy, fz, tx, ty, tz, pitchRad, maxRange, Model.SHELL, MAX_SPEED);
    }

    public static double[] launchVelocity(double fx, double fy, double fz, double tx, double ty, double tz,
                                          double pitchRad, double maxRange, Model model, double maxSpeed) {
        double dx = tx - fx;
        double dz = tz - fz;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0e-3 || distance > maxRange) {
            return null;
        }
        double speed = solveSpeed(distance, ty - fy, pitchRad, model, maxSpeed);
        if (Double.isNaN(speed)) {
            return null;
        }
        double horizontal = speed * Math.cos(pitchRad);
        return new double[] {dx / distance * horizontal, speed * Math.sin(pitchRad), dz / distance * horizontal};
    }

    /** Distance a gravity-less projectile launched at {@code speed} covers in {@code ticks} ticks. */
    public static double straightFlightDistance(double speed, int ticks, double drag) {
        // Drag is applied before each move, so tick k moves speed * drag^k.
        return speed * drag * (1 - Math.pow(drag, ticks)) / (1 - drag);
    }

    /** Result of a lead calculation: aim at this point; the bullet gets there after {@code ticks}. */
    public record Intercept(double x, double y, double z, int ticks) {
    }

    /**
     * Earliest point where a gravity-less bullet fired now from the muzzle can meet the shell.
     *
     * @return the aim point, or {@code null} if the shell cannot be reached within {@code maxTicks}
     */
    public static Intercept lead(State shell, double mx, double my, double mz, double bulletSpeed,
                                 double bulletDrag, int maxTicks) {
        State s = shell;
        for (int tick = 1; tick <= maxTicks; tick++) {
            s = s.step();
            double dx = s.x() - mx;
            double dy = s.y() - my;
            double dz = s.z() - mz;
            double needed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (straightFlightDistance(bulletSpeed, tick, bulletDrag) >= needed) {
                return new Intercept(s.x(), s.y(), s.z(), tick);
            }
        }
        return null;
    }
}
