package org.firstinspires.ftc.teamcode;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.teamcode.component.ChassisComponent;
import org.firstinspires.ftc.teamcode.component.GrabberComponent;
import org.firstinspires.ftc.teamcode.component.IntakeComponent;
import org.firstinspires.ftc.teamcode.component.LauncherComponent;
import org.firstinspires.ftc.teamcode.component.StackerComponent;

@TeleOp(name = "Teleop", group = "POC")
public class Teleop extends LinearOpMode {
    private OpMode op;
    private ChassisComponent chassisComponent;
    private IntakeComponent intakeComponent;
    private GrabberComponent grabberComponent;
    private LauncherComponent launcherComponent;
    private LauncherStateMachine launcherStateMachine;
    private Telemetry.Item log_angle;

    Odometry odometry;

    ElapsedTime runningTime;
    private StackerComponent stackerComponent;
    private StackerStateMachine stackerStateMachine;
    private ChassisComponent chassisComponent1;

    double power=1.0;

    boolean powershot_mode = false;
    boolean gamepad2_y_being_pressed = false;

    public void runOpMode(){

        this.telemetry.setAutoClear(false);

        grabberComponent = new GrabberComponent(this);
        grabberComponent.init();

        chassisComponent = new ChassisComponent(this);
        chassisComponent.init();
        chassisComponent.initIMU();

        launcherComponent = new LauncherComponent(this);
        launcherComponent.init();
        launcherStateMachine = new LauncherStateMachine(launcherComponent, op);

        intakeComponent = new IntakeComponent(this);
        intakeComponent.init();

        odometry = new Odometry(this,chassisComponent,50,75);

        stackerComponent = new StackerComponent(this);
        stackerComponent.init();
        stackerStateMachine = new StackerStateMachine(stackerComponent, op);
        log_angle = this.telemetry.addData("log_angle:", chassisComponent.getAngle() * 180 / Math.PI - 90);

        waitForStart();


        /*
        GamePad 1: Navigator
            a - reset the imu at current robot orientation
            left stick - field centric controls
            right stick - turning (only push to left or right)
            dpad up - move completely forward
            dpad down - move completely backward
            right bumper - intake IN
            left bumper - intake OUT

        GamePad 2: Internal motors
            right joystick - launcher:
                up = shoot, down = reverse
            right bumper - toggle whacker
            left bumper - full safeWhacker out and then in
            dpad up - stacker box up
            dpad down - stacker box down
            x - dump

            dpad right - claw open
            dpad left - claw close
            left stick - arm up/down based on stick up and down

         */
        while(opModeIsActive()){

            //
            // gamepad 1
            //

            if(gamepad1.a) {
                chassisComponent.initIMU();
            }
            if(gamepad1.x){log_angle.setValue(chassisComponent.getAngle() * 180 / Math.PI - 90);}

                if (Math.abs(gamepad1.left_stick_y) > 0.1 || Math.abs(gamepad1.left_stick_x) > 0.1
                    || (Math.abs(gamepad1.right_stick_x) > 0.1)) {
                double x = gamepad1.left_stick_x;
                double y = -gamepad1.left_stick_y; // note: joystick y is reversed.
                double turn = -gamepad1.right_stick_x; //for driver specifically arnav who has practiced the other way
                double power = Math.sqrt(x*x + y*y);
                power = (power > 0) ? power : Math.abs(turn);
                chassisComponent.fieldCentricDrive(x, y, power,false, turn);
            }
            else if (gamepad1.dpad_up) {
                chassisComponent.moveForward(power*0.5);
            }
            else if (gamepad1.dpad_down) {
                chassisComponent.moveBackward(power*0.5);
            }
            else {
               chassisComponent.stop();
           }

            if(gamepad1.right_bumper){
                intakeComponent.expel();
            }
            else if(gamepad1.left_bumper){
                intakeComponent.in();
            }
            else {
                intakeComponent.stop();
            }

            //
            //gamepad 2
            //

            if(gamepad2.x){
                stackerComponent.stackerDump();
            }
            if (gamepad2.right_bumper){
                stackerComponent.safeWhack();
            }
            else if(gamepad2.dpad_up){
                stackerComponent.stackerUp();
            }
            else if (gamepad2.dpad_down){
                stackerComponent.stackerDown();
            }

            if (gamepad2.left_bumper) {
                // Note: safeWhackThree sleeps for a while which introduces race condition between Driver 1 stopping
                // the robot right before driver 2 presses safeWhackThree. Robot continues to move during safeWhackThree
                // if driver 1 stop does not get registered in time.
                // Fix: Force robot to stop moving before we go in this long (sleep) thread.
                // Do this only if launcher is also running to ensure the intent is to shoot rather than unjam.
                if (gamepad2.right_stick_y < 0)
                    chassisComponent.stop();
                stackerComponent.safeWhackThree();
            }

            if (gamepad2.y) {
                if (!gamepad2_y_being_pressed) {
                    gamepad2_y_being_pressed = true;
                    powershot_mode = !powershot_mode;
                }
            }
            else
                gamepad2_y_being_pressed = false;

            if(gamepad2.right_stick_y < 0){
                if (powershot_mode)
                    launcherComponent.powershotShoot();
                else
                    launcherComponent.shoot();
            }
            else if (gamepad2.right_stick_y > 0){
                launcherComponent.reverse();
            }
            else {
                launcherComponent.stop();
            }

            //wobble arm controls
            if(gamepad2.dpad_left){
                grabberComponent.clawOpen();
            }
            else if(gamepad2.dpad_right){
                grabberComponent.clawClose();
            }
            if(Math.abs(gamepad2.left_stick_y) > 0.1 ){
                if(gamepad2.left_stick_y < 0){
                    grabberComponent.armStraight();
                }
                if(gamepad2.left_stick_y > 0){
                    grabberComponent.armDown();
                }
            }

            /* full shooting command
                1. starting the launcher; setting it with a runtime of 10 sec
                2. setting the number of whacks to 3
                3. Stacker moves up
                4. If the launcher has reached max speed; then start whacking
                5. Once we're finished whacking, move the stacker (now empty) down
                6. Stop the launcher
             */
/*
            if(gamepad2.y){
                runningTime = new ElapsedTime();
                runningTime.reset();
                launcherStateMachine.setRunningTime(10000);
                launcherStateMachine.run();
                stackerStateMachine.setWhacks(3);

                if (launcherStateMachine.getState() == LauncherStateMachine.State.REACHED_MAX ){
                    stackerStateMachine.run();
                }
                launcherStateMachine.stop();
            }

*/

            this.telemetry.update();
        }


    }
}
