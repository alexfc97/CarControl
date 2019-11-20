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
                // Demonstration of Bridge
                // The resulting behavior shows that cars will drive onto the bridge once they reach it. However at a
                // certain point around K = 3 or 4, A deadlock will happen. This is because step 5 doesn't account for
                // deadlocks being able to happen.
                cars.startAll();
                cars.setLimit(5);
                sleep(30000);
                cars.setLimit(4);
                sleep(30000);
                cars.setLimit(3);
                sleep(30000);
                cars.setLimit(2);
                sleep(30000);
                cars.setLimit(1);
                sleep(30000);
                cars.setLimit(0);
                sleep(3000);
                break;

            case 2:
                // Demonstration of how SetLimit is dynamic
                // The resulting behavior shows that cars will drive onto the bridge once they reach it. However at a
                // certain point around K = 3 or 4, A deadlock will happen. In this test, we can see how SetLimit is
                // able to solve this deadlock.
                cars.startAll();
                cars.setLimit(4);
                sleep(30000);
                cars.setLimit(3);
                sleep(20000);
                cars.setLimit(5);
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



