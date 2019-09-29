package ca.mcgill.ecse211.demo;

import java.awt.geom.Point2D;
import ca.mcgill.ecse211.canhandling.Claw;
import ca.mcgill.ecse211.localization.LightLocalizer;
import ca.mcgill.ecse211.localization.UltrasonicLocalizer;
import ca.mcgill.ecse211.localization.WallLocalizer;
import ca.mcgill.ecse211.navigation.Navigation;
import ca.mcgill.ecse211.odometer.Odometer;
import ca.mcgill.ecse211.odometer.OdometerExceptions;
import ca.mcgill.ecse211.odometer.OdometryCorrection;
import ca.mcgill.ecse211.wifi.GameSettings;
import lejos.hardware.Button;
import lejos.hardware.Sound;
import lejos.hardware.ev3.LocalEV3;
import lejos.hardware.lcd.TextLCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.sensor.EV3ColorSensor;
import lejos.hardware.sensor.EV3GyroSensor;
import lejos.hardware.sensor.EV3UltrasonicSensor;
import lejos.hardware.sensor.SensorModes;
import lejos.robotics.MirrorMotor;
import lejos.robotics.RegulatedMotor;
import lejos.robotics.SampleProvider;

/**
 * The final demo class stores the main method for 
 * the robot, as well as a series of run modes,
 * including run modes for the final demo, beta demo,
 * and some testing cases.
 * @author jacob
 *
 */
public class FinalDemo {
  /**
   * Sets whether or not debug sounds should be played
   */
  public static final boolean DEBUG_ON = true;
  /**
   * The robot's left motor
   */
  public static final RegulatedMotor LEFT_MOTOR = 
      MirrorMotor.invertMotor(new EV3LargeRegulatedMotor(LocalEV3.get().getPort("C")));
  /**
   * The robot's right motor
   */
  public static final RegulatedMotor RIGHT_MOTOR =
      MirrorMotor.invertMotor(new EV3LargeRegulatedMotor(LocalEV3.get().getPort("B")));
  /**
   * The motor used to spin cans
   */
  public static final EV3LargeRegulatedMotor CAN_MOTOR =
      new EV3LargeRegulatedMotor(LocalEV3.get().getPort("A"));
  /**
   * The robot's color-detecting light sensor
   */
  public static final SampleProvider COLOR_SENSOR;
  /**
   * The robot's line-detecting light sensor
   */
  public static final SampleProvider LINE_SENSOR;
  /**
   * The robot's front-facing ultrasonic sensor
   */
  public static final SampleProvider US_FRONT;
  /**
   * Represents the radius of each wheel, in cm
   */
  public static final double WHEEL_RAD = 2.05; //was 2.18
  /**
   * Represents the distance between the wheels, in cm
   */
  public static final double TRACK_WITHOUT_CAN = 10.35;
  /**
   * The model for the track when a can is held
   * This changes because the torque from the can
   * shifts where the robot pivots, requiring us to 
   * update our model.
   */
  public static final double TRACK_WITH_CAN = 10.6;
  /**
   * The model for the track. We say model because it
   * represents the wheel separation on an idealized model
   * of the robot.
   */
  public static double TRACK = TRACK_WITHOUT_CAN;
  /**
   * The offset between the robot turning center and the line sensor in
   * the Y direction, in cm. Note: magnitude only.
   */
  public static final double LINE_OFFSET_Y = 7;
  /**
   * The offset between the robot turning center and the line sensor in
   * the X direction, in cm. Note: magnitude only.
   */
  public static final double LINE_OFFSET_X = 5.5;
  /**
   * The can classifier used by the program
   */
  public static final Claw CLAW = new Claw();
  /**
   * The acceleration value for the locomotive motors
   */
  public static final int ACCELERATION = 1500;
  /**
   * The port for the gyroscope
   */
  public static final EV3GyroSensor GYRO =  new EV3GyroSensor(LocalEV3.get().getPort("S4"));
  /**
   * The robots gyroscope sample provider
   */
  public static final SampleProvider GYRO_DATA = GYRO.getAngleMode();
  
  static {
    @SuppressWarnings("resource")
    SensorModes colorSensorMode = new EV3ColorSensor(LocalEV3.get().getPort("S3"));
    COLOR_SENSOR = colorSensorMode.getMode("RGB");

    @SuppressWarnings("resource")
    SensorModes lineSensorMode = new EV3ColorSensor(LocalEV3.get().getPort("S1"));
    LINE_SENSOR = lineSensorMode.getMode("Red");

    
    SensorModes usSensor = new EV3UltrasonicSensor(LocalEV3.get().getPort("S2"));
    US_FRONT = usSensor.getMode("Distance");
  }
  /**
   * The LCD used to output during the robot's journey
   */
  public static final TextLCD LCD = LocalEV3.get().getTextLCD();
  /**
   * The Odometry correction system for the robot
   */
  public static final OdometryCorrection OC = getOC();
  /**
   * The navigation thread used by the robot
   */
  public static final Navigation NAV = getNav();
  private static Navigation getNav() {
    try {
      return new Navigation();
    } catch (OdometerExceptions e) {
      return null;
    }
  }
  private static OdometryCorrection getOC() {
    try {
      return new OdometryCorrection();
    } catch (OdometerExceptions e) {
      return null;
    }
  }
  /**
   * Distance between lines in cm
   */
  public static final float GRID_WIDTH = 30.48f;

  /**
   * The main method. Runs the final demo mode.
   * @param args not used
   * @throws OdometerExceptions 
   * @throws InterruptedException
   */
  public static void main(String[] args) throws OdometerExceptions, InterruptedException {
    finalDemo();
    System.exit(0);
  }
  
  /**
   * Runs the code associated with the final demo. 
   */
  private static void finalDemo() throws OdometerExceptions, InterruptedException{
	Sound.setVolume(0);
    init();
    OC.setOn(false);
    localizeWall();
    CLAW.calibrate();
    CLAW.close();
    OC.setOn(true);
    CanFinder cf = new CanFinder();
    while (true) {
      cf.goToSearchArea(true);
      beepNTimes(3);
      cf.search();
      OC.setOn(false);
      while (!cf.grabNextCan()) {
        if (DEBUG_ON) {
          Sound.buzz();
        }
        cf.goToSearchArea(false);
        cf.search();
      }
      TRACK = TRACK_WITH_CAN;
      CLAW.classifyAndBeep();
      OC.setOn(true);
      cf.dropOffCan();
      TRACK = TRACK_WITHOUT_CAN;
      Point2D startCorner = GameSettings.getStartingCornerPoint();
      NAV.travelTo(startCorner.getX(), startCorner.getY());
      beepNTimes(5);
      NAV.waitUntilDone();
      (new LightLocalizer(startCorner.getX(),
                          startCorner.getY())).run();
    }
  }

  /**
   * Initializes the robot by getting game settings,
   * starting the odometry thread & the nav thread
   * Also sets the acceleration of the motors
   * @throws OdometerExceptions
   */
  private static void init() throws OdometerExceptions {
    (new Thread(Odometer.getOdometer())).start();
    GameSettings.init();
    NAV.start();
    OC.start();
    LEFT_MOTOR.setAcceleration(ACCELERATION);
    RIGHT_MOTOR.setAcceleration(ACCELERATION);
  }

  /**
   * Localizes the robot using ultrasonic and light localization
   * Automatically updates the position to convey the starting corner
   * of the robot iff the game settings have been initialized.
   * 
   * This is not used on demo day.
   * @throws OdometerExceptions
   */
  private static void localizeLight() throws OdometerExceptions {
    (new UltrasonicLocalizer()).run();
    (new LightLocalizer(GRID_WIDTH,GRID_WIDTH, false)).run();
    beepNTimes(3);
    NAV.waitUntilDone();
    updateCorner();
  }

  /**
   * Localizes the robot using ultrasonic and wall localization
   * Automatically updates the position to convey the starting corner
   * of the robot iff the game settings have been initialized
   * @throws OdometerExceptions
   */
  private static void localizeWall() throws OdometerExceptions {
    (new UltrasonicLocalizer()).run();
    WallLocalizer.run();
    NAV.travelTo(GRID_WIDTH, GRID_WIDTH);
    NAV.waitUntilDone();
    beepNTimes(3);
    NAV.waitUntilDone();
    updateCorner();
  }
  
  /**
   * This method updates the odometer based on the
   * corner the robot is in. We assume the robots odometry
   * is set up as if it is in corner 0 (ie, x < GRID_WIDTH, y < GRID_WIDTH),
   * and values for x, y and t are mdofied to reflect the real
   * location of the robot.
   * @throws OdometerExceptions
   */
  private static void updateCorner() throws OdometerExceptions {
    Odometer o = Odometer.getOdometer();
    double x =  o.getXYT()[0];
    double y = o.getXYT()[1];
    double t = o.getXYT()[2];
    if (GameSettings.initialized) {
      switch (GameSettings.corner) {
        case 1:
          o.setX(15*GRID_WIDTH-y);
          o.setY(x);
          o.setTheta(t + 270);
          break;
        case 2:
          o.setX(15*GRID_WIDTH-x);
          o.setY(9*GRID_WIDTH-y);
          o.setTheta(t + 180);
          break;
        case 3:
          o.setX(y);
          o.setY(9*GRID_WIDTH-x);
          o.setTheta(t + 90);
          break;
        default:
          break;
      }
    }
  }



  /**
   * Calculates the center of the sensor from the position of the
   * robot, denoted as an array
   * @param robot An array of the form {x,y,t} representing the
   * position of the sensor
   * @return The location of the sensor in the form {x,y,t}
   */
  public static double[] toSensor(double[] robot) {
    double[] result = new double[3];
    if (robot.length == 3) {
      double t = robot[2];
      result[0] = robot[0] 
          - FinalDemo.LINE_OFFSET_X * Math.cos(Math.toRadians(t))
          - FinalDemo.LINE_OFFSET_Y * Math.sin(Math.toRadians(t));
      result[1] = robot[1] 
          + FinalDemo.LINE_OFFSET_X * Math.sin(Math.toRadians(t))
          - FinalDemo.LINE_OFFSET_Y * Math.cos(Math.toRadians(t));
      result[2] = t;
    }
    return result;
  }

  /**
   * Calculates the center of the robot from the position of the
   * line sensor, denoted as an array
   * @param sensor An array of the form {x,y,t} representing the
   * position of the sensor
   * @return
   */
  public static double[] toRobot(double[] sensor) {
    double[] result = new double[3];
    if (sensor.length == 3) {
      double t = sensor[2];
      result[0] = sensor[0] 
          + FinalDemo.LINE_OFFSET_X * Math.cos(Math.toRadians(t))
          + FinalDemo.LINE_OFFSET_Y * Math.sin(Math.toRadians(t));
      result[1] = sensor[1] 
          - FinalDemo.LINE_OFFSET_X * Math.sin(Math.toRadians(t))
          + FinalDemo.LINE_OFFSET_Y * Math.cos(Math.toRadians(t));
      result[2] = t;
    }
    return result;
  }

  /**
   * Beeps n times
   * @param n the number of times to beep ( :O )
   */
  public static void beepNTimes(int n) {
	Sound.setVolume(100);
    for (int i = 0; i < n; i++) {
      Sound.beep();
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {}
    }
    Sound.setVolume(0);
  }


  /*
   * ***********************
   * TESTING METHODS
   * ***********************
   */

  /**
   * Drives in a square pf side length 5
   * For testing navigation & odometry
   */
  private static void squareDrive() {
    NAV.travelTo(1*GRID_WIDTH, 5*GRID_WIDTH);
    NAV.waitUntilDone();
    NAV.travelTo(5*GRID_WIDTH,5*GRID_WIDTH);
    NAV.waitUntilDone();
    NAV.travelTo(5*GRID_WIDTH, 1*GRID_WIDTH);
    NAV.waitUntilDone();
    NAV.travelTo(1*GRID_WIDTH, 1*GRID_WIDTH);
    NAV.waitUntilDone();
  }
  
  /**
   * Drives in a triangle of side length 5
   * For testing navigation & odometry
   */
  private static void triangleDrive() {
    NAV.travelTo(1*GRID_WIDTH, 5*GRID_WIDTH);
    NAV.waitUntilDone();
    NAV.travelTo(5*GRID_WIDTH,5*GRID_WIDTH);
    NAV.waitUntilDone();
    NAV.travelTo(1*GRID_WIDTH, 1*GRID_WIDTH);
    NAV.waitUntilDone();
  }

  /**
   * Spins the robot around 360 * x, where
   * x is a number of times
   * For testing track parameters
   * to test the parameters for the wheels
   */
  private static void rotateX(int x) {
    int t = 0;
    for (int i = 0; i < 3 * x + 1; i++) {
      NAV.turnTo(t);
      t = (t + 120) % 360;
    }
  }
  
  /**
   * Sets the odometer to 1,1,0
   * For testing features without considering efficacy of localization
   * @throws OdometerExceptions 
   */
  private static void resetOdo() throws OdometerExceptions {
    Odometer.getOdometer().setXYT(GRID_WIDTH, GRID_WIDTH, 0);
  }
}
