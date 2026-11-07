#!/usr/bin/env bash
set -euo pipefail
# Compile-only evidence. Does not load DJI classes, talk to aircraft, or claim runtime support.
root="$(cd "$(dirname "$0")/.." && pwd)"
jar="${DJI_PROVIDED_JAR:-$root/local-maven/com/dji/dji-sdk-v5-aircraft-provided/5.17.0/dji-sdk-v5-aircraft-provided-5.17.0.jar}"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cat > "$work/EventKeys.java" <<'JAVA'
import dji.sdk.keyvalue.key.*;
import dji.sdk.keyvalue.value.common.*;
final class EventKeys {
  DJIKeyInfo<Boolean> flying = FlightControllerKey.KeyIsFlying;
  DJIKeyInfo<Boolean> motors = FlightControllerKey.KeyAreMotorsOn;
  DJIKeyInfo<LocationCoordinate2D> location = FlightControllerKey.KeyAircraftLocation;
  DJIKeyInfo<LocationCoordinate3D> location3d = FlightControllerKey.KeyAircraftLocation3D;
  DJIKeyInfo<Double> altitude = FlightControllerKey.KeyAltitude;
  DJIKeyInfo<Velocity3D> velocity = FlightControllerKey.KeyAircraftVelocity;
  DJIKeyInfo<Integer> batteryPercent = BatteryKey.KeyChargeRemainingInPercent;
  DJIKeyInfo<Integer> motorElapsed = FlightControllerKey.KeyFlightTimeInSeconds;
  DJIKeyInfo<String> fcSerial = FlightControllerKey.KeySerialNumber;
  DJIKeyInfo<String> productSerial = ProductKey.KeySerialNumber;
}
JAVA
"${java_bin}javac" -cp "$jar" -d "$work" "$work/EventKeys.java"
echo 'SDK 5.17 public key signature compile: PASS (not a flight/hardware test)'
sha256sum "$jar"
