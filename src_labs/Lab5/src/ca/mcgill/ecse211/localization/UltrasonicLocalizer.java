package ca.mcgill.ecse211.localization;
import lejos.hardware.Sound;
import ca.mcgill.ecse211.lab5.AveragedBuffer;
import ca.mcgill.ecse211.lab5.Lab5;
import ca.mcgill.ecse211.navigation.Navigation;
import ca.mcgill.ecse211.odometer.Odometer;
import ca.mcgill.ecse211.odometer.OdometerExceptions;
import ca.mcgill.ecse211.odometer.OdometryCorrection;

/**
 * This class represents a routine used by the robot
 * to calibrate the theta value of the odometer, using
 * the ultrasonic sensor.
 * 
 * This will only use falling edge detection
 * 
 * @author jacob silcoff & helen lin
 *
 */
public class UltrasonicLocalizer {

  /**
   * The time (ms) between polling the sensor
   */
  public static final int SLEEP_TIME = 15;
  /**
   * The distance (cm) below which the robot assumes it is looking at the wall
   */
  public static final double DETECTION_DISTANCE = 30;
  /**
   * The motor speed (deg/s) used by the robot when turning
   */
  private static final int ROTATE_SPEED = 80;


  private Odometer odo;
  private Navigation nav;
  private AveragedBuffer<Float> samples;



  /**
   * Creates an ultrasonic sensor localizer instance for rising or falling edge localization
   * 
   * @param oc The odometry correction thread to be turned off during this process
   */
  public UltrasonicLocalizer(OdometryCorrection oc) {
    samples = new AveragedBuffer<Float>(5);
    try {
      odo = Odometer.getOdometer();
    } catch (OdometerExceptions e) {
      e.printStackTrace();
    }

    // start a new navigation thread
    try {
      this.nav = new Navigation(oc);
    } catch (OdometerExceptions e) {
      e.printStackTrace();
    }
    nav.start();
  }

  /**
   * Creates an ultrasonic sensor localizer instance for rising or falling edge localization
   */
  public UltrasonicLocalizer() {
    samples = new AveragedBuffer<Float>(5);
    try {
      odo = Odometer.getOdometer();
    } catch (OdometerExceptions e) {
      e.printStackTrace();
    }

    // start a new navigation thread
    try {
      this.nav = new Navigation(null);
    } catch (OdometerExceptions e) {
      e.printStackTrace();
    }
    nav.start();
  }


  /**
   * Turns the robot clockwise or counterclockwise
   * until a falling or rising edge is detected, as 
   * measured by the ultrasonic sensor, and then returns
   * the angle of that edge.
   * @param rising True to find the rising edge, false for falling
   * @param cw True to turn clockwise, false for counter clockwise
   * @return The angle of the edge found
   */
  public double getEdge(boolean cw) {
    int dir = cw? 1 : -1;
    nav.setSpeeds(dir * ROTATE_SPEED, - dir * ROTATE_SPEED); //set clockwise or counterclockwise turn
    Lab5.LCD.drawString("STAGE 1", 0, 4);
    double reading = readUS();
    //loops to not be thrown off by 1 bad reading
    boolean seesWall = false;
    for (int i = 0; i < 20; i++) {
      while ((reading < DETECTION_DISTANCE) || reading == -1) {
        seesWall = true;
        sleep(); 
        reading = readUS(); //keep turning + updating readings
      }
      sleep(); 
    }
    //ensures you are well past the rising edge
    if (seesWall) {
      nav.turnTo((odo.getXYT()[2] + dir * 45 + 360)%360);
      nav.setSpeeds(dir * ROTATE_SPEED, - dir * ROTATE_SPEED);
    }


    Lab5.LCD.drawString("STAGE 2", 0, 4);
    while ((reading > DETECTION_DISTANCE) || reading == -1) {
      sleep();
      reading = readUS(); //final readings
    }
    nav.setSpeeds(0, 0);// stop

    Lab5.LCD.drawString("Edge detected", 0, 4);
    Sound.beep(); //audio notification

    nav.setSpeeds(0, 0); //stop robot
    return odo.getXYT()[2];
  }

  /**
   * Calculates north heading according to the edges found
   * 
   * @param theta1 first angle detected from localization
   * @param theta2 2nd angle detected from localization
   */
  private double localizeNorth(double theta1, double theta2) {
    double avgAngle = (theta1 + theta2) / 2;

    if (minAngle(avgAngle, theta1) > 90) {
      avgAngle = (avgAngle + 180) % 360;
    }

    return (avgAngle + 135 + 360) % 360;
  }

  /**
   * Returns the min angle between two angles
   * @param t1 the first angle
   * @param t2 the second angle
   */
  private static double minAngle(double t1, double t2) {
    double ang = (t1 - t2 + 360) % 360;
    if (ang > 180) {
      return 360 - ang;
    } else {
      return ang;
    }
  }

  /**
   * Executes the localization routine
   */
  public void run() {

    //Find first edge
    double theta1 = getEdge(false);
    // switch directions and turn until another edge is detected
    double theta2 = getEdge(true);

    // correct current theta
    double realAngle = (odo.getXYT()[2] - localizeNorth(theta1, theta2) + 360) % 360;
    odo.setXYT(0, 0, realAngle); 

    // turn to localized North
    nav.turnTo(0);
    if (readUS() < DETECTION_DISTANCE) {
      odo.setTheta(180);
    }
    nav.turnTo(0);
    nav.end();
  }

  /**
   * Sleeps for the default amount of time
   */
  private void sleep() {
    try {
      Thread.sleep(SLEEP_TIME);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Polls the ultrasonic sensor and returns the result
   * 
   * @return The US reading in cm
   */
  private float readUS() {
    float[] usData = new float[Lab5.US_FRONT.sampleSize()];
    Lab5.US_FRONT.fetchSample(usData, 0);
    Lab5.LCD.drawString("US:" + (usData[0] * 100.0) + ".........", 0, 7);
    samples.add((usData[0] * 100f));
    if (usData[0] == 255) {
      return -1;
    }
    return usData[0] * 100f;
  }

  /**
   * Polls the ultrasonic sensor and returns the result,
   * which can use the averaged filter if desired
   * @param buffered True to use rolling average filter, false to get simple reading
   * @return The US reading (cm)
   */
  public float readUS(boolean buffered) {
    if (buffered) {
      readUS();
      return (float) samples.getAvg();
    } else {
      return readUS();
    }
  }

}