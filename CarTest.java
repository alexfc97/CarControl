//Prototype implementation of Car Test class
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2019

//Hans Henrik Lovengreen       Oct 8, 2019

public class CarTest extends Thread {

    CarTestingI cars;
    int testno;

    public CarTest(CarTestingI ct, int no) {
        cars = ct;
        testno = no;
    }

    public void run() {
        try {
            switch (testno) { 
            case 0:
                // Demonstration of startAll/stopAll.
                // Should let the cars go one round (unless very fast)
                cars.startAll();
                sleep(3000);
                cars.stopAll();
                break;

            case 1:
                // Demonstration of bumping and alley solution
                // The resulting behavior should show how cars do not bump into each other.
                // The test should also show how the alley synchronization has been implemented.
                // If cars going the opposite direction are already in the alley, then a car will wait until they have
                // all left.
                cars.startAll();
                break;

            case 2:
                // Demonstration of The barrier solution
                // The resulting behavior should show how after the barrier is turned on and each car has arrived at the
                // barrier, Each car is released until the test is repeated again and again.
                cars.startAll();
                sleep(1000);
                cars.barrierOn();
                break;
            case 19:
                // Demonstration of speed setting.
                cars.println("Setting high speeds");
                for (int i = 1; i < 9; i++) {
                    cars.setSpeed(i,20.0);
                    cars.setVariation(i,20);
                };
                break;

            default:
                cars.println("Test " + testno + " not available");
            }

            cars.println("Test ended");

        } catch (Exception e) {
            System.err.println("Exception in test: "+e);
        }
    }

}



