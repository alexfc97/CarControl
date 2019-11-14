//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2017

//Hans Henrik Lovengreen       Oct 8, 2019

import java.awt.Color;

@SuppressWarnings("all")
class Gate {

    Semaphore g = new Semaphore(0);
    Semaphore e = new Semaphore(1);
    boolean isopen = false;

    public void pass() throws InterruptedException {
        g.P();
        g.V();
    }

    public void open() {
        try { e.P(); } catch (InterruptedException e) {}
        if (!isopen) { g.V();  isopen = true; }
        e.V();
    }

    public void close() {
        try { e.P(); } catch (InterruptedException e) {}
        if (isopen) {
            try { g.P(); } catch (InterruptedException e) {}
            isopen = false;
        }
        e.V();
    }
}

// opret array med semaphores, som tilsvarer antal felter som en bil tager når det kører ind i det
@SuppressWarnings("all")
class Conductor extends Thread {

    double basespeed = 6.0;          // Tiles per second
    double variation =  50;          // Percentage of base speed

    CarDisplayI cd;                  // GUI part

    int no;                          // Car number
    Pos startpos;                    // Start position (provided by GUI)
    Pos barpos;                      // Barrier position (provided by GUI)
    Color col;                       // Car  color
    Gate mygate;                     // Gate at start position

    Pos curpos;                      // Current position
    Pos newpos;                      // New position to go to
    Semaphore[][] sFields;
    CarControl.Alley alley;
    CarControl.Barrier barrier;
    CarControl.Bridge bridge;


    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] sFields, CarControl.Alley alley, CarControl.Barrier barrier, CarControl.Bridge bridge) {

        this.no = no;
        this.cd = cd;
        this.sFields = sFields;
        this.alley = alley;
        this.barrier = barrier;
        this.bridge = bridge;
        mygate = g;

        startpos = cd.getStartPos(no);
        barpos   = cd.getBarrierPos(no);  // For later use

        col = chooseColor();

        // special settings for car no. 0
        if (no==0) {
            basespeed = -1.0;
            variation = 0;
        }
    }

    public synchronized void setSpeed(double speed) {
        basespeed = speed;
    }

    public synchronized void setVariation(int var) {
        if (no != 0 && 0 <= var && var <= 100) {
            variation = var;
        }
        else
            cd.println("Illegal variation settings");
    }

    synchronized double chooseSpeed() {
        double factor = (1.0D+(Math.random()-0.5D)*2*variation/100);
        return factor*basespeed;
    }

    Color chooseColor() {
        return Color.blue; // You can get any color, as longs as it's blue
    }

    Pos nextPos(Pos pos) {
        // Get my track from display
        return cd.nextPos(no,pos);
    }

    boolean atGate(Pos pos) {
        return pos.equals(startpos);
    }

    public void takeSpace(int row, int col, int no) throws InterruptedException {
        sFields[row][col].P();
    }
    public void freeSpace(int row, int col, int no) throws InterruptedException {
        sFields[row][col].V();
    }

    public void run() {
        try {
            CarI car = cd.newCar(no, col, startpos);
            curpos = startpos;
            cd.register(car);

            while (true) {

                if (atGate(curpos)) {
                    mygate.pass();
                    car.setSpeed(chooseSpeed());
                }

                newpos = nextPos(curpos);

                takeSpace(newpos.row, newpos.col, no);

                car.driveTo(newpos);

                freeSpace(curpos.row, curpos.col, no);

                if ( no < 5 && newpos.row == 9 && newpos.col == 0 ) {
                    bridge.enter(no);
                }
                else if ( no > 4 && newpos.row == 10 && newpos.col == 4 ) {
                    bridge.enter(no);
                }
                else if ( no < 5 && curpos.row == 9 && curpos.col == 3 ) {
                    bridge.leave(no, newpos.row, newpos.col);
                }
                else if ( no > 4 && curpos.row == 10 && curpos.col == 1 ){
                    bridge.leave(no, newpos.row , newpos.col);
                }

                if (newpos.row == 9 && newpos.col == 0 && no > 4){
                    bridge.CarWaiting = false;
                };

                if((newpos.row==10 && newpos.col==0) || (newpos.row==2 && newpos.col==1) || (newpos.row==1 && newpos.col==3)) {
                    alley.enter(no);
                }

                if (no<=4) {
                    if(curpos.row==9 && curpos.col==0) {
                        alley.leave(no);
                    }
                }
                if (no>=5) {
                    if(curpos.row==0 && curpos.col==2) {
                        alley.leave(no);
                    }
                }

                if(barrier.barrierActivated) {
                    if(no>=5 && newpos.row==5 && newpos.col>7) {
                        barrier.sync(no);
                    }
                    else if (no<=4 && newpos.row==6 && newpos.col>2) {
                        barrier.sync(no);
                    }
                }

                curpos = newpos;

            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }

}
@SuppressWarnings("all")
public class CarControl implements CarControlI{

    public void initSemFields(Semaphore[][] sFields) {
        for (int i = 0; i < 11; i++)
            for (int j = 0; j < 12; j++)
                sFields[i][j] = new Semaphore(1);

    }

    public void initBarrierSemaphore(Semaphore[] barrierSemaphore) {
        for (int i = 0; i <= 8; i++)
            barrierSemaphore[i] = new Semaphore(0);
    }

    class Bridge {
        int limit;
        int carsatbridge = 0;
        boolean CarWaiting = false;

        public synchronized void enter(int no) throws InterruptedException {
            if(carsatbridge >= limit){
                wait();
            }
            else if (CarWaiting && no > 4){
                while(CarWaiting) {
                    wait();
                }
                CarWaiting = false;
            }
            carsatbridge++;
        }

        public synchronized void leave(int no, int row, int col){
            if (row==10 && col==0 && no > 4) {
                CarWaiting = true;
            }
            notify();
            carsatbridge--;
        }

        public synchronized void setLimit(int k){
            if (k > limit) {
                for ( int i = 0 ; i < k-limit ; i++){
                    notify();
                }
            }
            this.limit = k;
        }
    }

    class Barrier {

        boolean barrierActivated = false;
        int carsAtBarrier = 0;

        // Wait for others to arrive (if barrier active)
        public synchronized void sync(int no) throws InterruptedException {
            carsAtBarrier++;
            if(carsAtBarrier==9) {
                notifyAll();
                carsAtBarrier=0;
            } else {
                wait();
            }
        }

        // Activate barrier
        public void on() {
            barrierActivated = true;
        }

        // Deactivate barrier
        public synchronized void off() {
            barrierActivated = false;
            notifyAll();
            carsAtBarrier = 0;
        }

    }

    class Alley {

        int carsInValley = 0;
        boolean LowerPassageAllowed = true;
        boolean HigherPassageAllowed = true;
        boolean oneWaiting = false;

        public synchronized void enter(int no) throws InterruptedException {
            if(LowerPassageAllowed && HigherPassageAllowed) {
                if(no<=4) {
                    LowerPassageAllowed = false;
                }
                else {
                    HigherPassageAllowed = false;
                }
            }
            else if (LowerPassageAllowed && no<=4) {
                if(oneWaiting) {
                    wait();
                } else {
                    oneWaiting = true;
                    wait();
                }
                HigherPassageAllowed = true;
                LowerPassageAllowed = false;
                oneWaiting = false;

            }
            else if (HigherPassageAllowed && no>=5) {
                wait();
                HigherPassageAllowed = false;
                LowerPassageAllowed = true;
            }
            carsInValley++;
        }

        public synchronized void leave(int no) throws InterruptedException {
            carsInValley--;
            if(carsInValley==0) {
                LowerPassageAllowed = true;
                HigherPassageAllowed = true;
                notifyAll();
            }
        }
    }

    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] sFields = new Semaphore[11][12];
    Semaphore[] barrierSemaphore = new Semaphore[9];
    Alley alleysync = new Alley();
    Barrier barrier = new Barrier();
    Bridge bridge = new Bridge();

    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];

        initSemFields(sFields);
        initBarrierSemaphore(barrierSemaphore);

        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], sFields, alleysync, barrier, bridge);
            conductor[no].setName("Conductor-" + no);
            conductor[no].start();
        }
    }

    public void startCar(int no) {
        gate[no].open();
    }

    public void stopCar(int no) {
        gate[no].close();
    }

    public void barrierOn() {
        barrier.on();
    }

    public void barrierOff() {
        barrier.off();
    }

    public void setLimit(int k) {
        bridge.setLimit(k);
    }

    public void barrierShutDown() {
        cd.println("Barrier shut down not implemented in this version");
        // This sleep is for illustrating how blocking affects the GUI
        // Remove when shutdown is implemented.
        try { Thread.sleep(3000); } catch (InterruptedException e) { }
        // Recommendation:
        //   If not implemented call barrier.off() instead to make graphics consistent
    }

    public void removeCar(int no) {
        cd.println("Remove Car not implemented in this version");
    }

    public void restoreCar(int no) {
        cd.println("Restore Car not implemented in this version");
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) {
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) {
        conductor[no].setVariation(var);
    }

}






