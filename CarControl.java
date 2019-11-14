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
    Boolean[] removeCarBoolean;
    Boolean[] restoreCarBoolean;
  
    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] sFields, CarControl.Alley alley, CarControl.Barrier barrier, Boolean[] removeCarBoolean, Boolean[] restoreCarBoolean, CarControl.Bridge bridge) {

        this.no = no;
        this.cd = cd;
        this.sFields = sFields;
        this.alley = alley;
        this.barrier = barrier;
        this.bridge = bridge;
        this.removeCarBoolean = removeCarBoolean;
        this.restoreCarBoolean = restoreCarBoolean;
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
    boolean atBarrier(Pos pos) {
        return pos.equals(barpos);
    }

    public void takeSpace(int row, int col) throws InterruptedException {
        sFields[row][col].P();
    }
    public void freeSpace(int row, int col) {
        sFields[row][col].V();
    }
    public synchronized void removeCar(int no) {
        if(!removeCarBoolean[no]) {
            removeCarBoolean[no] = true;
            try {
                alley.wakeUpForRemoval();
            } catch (Exception e) {
                cd.println("Exception in Car no. " + no);
            }
            freeSpace(newpos.row, newpos.col);
        } else {
            cd.println("Car already removed");
        }
    }
    public void restoreCar(int no) {
        if(removeCarBoolean[no]) {
            restoreCarBoolean[no] = true;
        } else {
            cd.println("Car already registered");
        }
    }

    public void run() {
        try {
            CarI car = cd.newCar(no, col, startpos);
            curpos = startpos;
            cd.register(car);
            boolean hasBeenRemoved = false;
            boolean removedWhileWaitingForTile = false;
            boolean inAlley = false;

            while (true) {

                if(!removeCarBoolean[no]) {
                    if (atGate(curpos)) {
                        mygate.pass();
                        car.setSpeed(chooseSpeed());

                    }

                    newpos = nextPos(curpos);

                    takeSpace(newpos.row, newpos.col);

                    // if the car has been woken up from takeSpace to be removed it is supposed to skip rest of this segment
                    if(!removeCarBoolean[no]) {
                        car.driveTo(newpos);

                        freeSpace(curpos.row, curpos.col);
                      
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
                    bridge.leaving();
                };

                        if ((newpos.row == 10 && newpos.col == 0) || (newpos.row == 2 && newpos.col == 1) || (newpos.row == 1 && newpos.col == 3)) {
                            alley.enter(no);
                            if(!removeCarBoolean[no]) {
                                inAlley = true;
                            }
                        }

                        if (no <= 4) {
                            if (curpos.row == 9 && curpos.col == 0) {
                                alley.leave(no);
                                inAlley = false;
                            }
                        }
                        if (no >= 5) {
                            if (curpos.row == 0 && curpos.col == 2) {
                                alley.leave(no);
                                inAlley = false;
                            }
                        }

                        if (barrier.barrierActivated) {
                            if (atBarrier(newpos)) {
                                barrier.sync(no);
                            }
                        }

                        curpos = newpos;
                    } else {
                        removedWhileWaitingForTile = true;
                    }
                } else {
                    if (!hasBeenRemoved) {
                        cd.deregister(car);
                        if(inAlley) {
                            alley.leave(no);
                        }
                        if(removedWhileWaitingForTile) {
                            freeSpace(curpos.row, curpos.col);
                            removedWhileWaitingForTile = false;
                        }
                        hasBeenRemoved = true;
                    }
                    // for some reason cars wont be restored unless there is some line here
                    System.out.println("this has to be here to work");
                    if (restoreCarBoolean[no]) {
                        removeCarBoolean[no] = false;
                        hasBeenRemoved = false;
                        restoreCarBoolean[no] = false;
                        car = cd.newCar(no, col, startpos);
                        curpos = startpos;
                        takeSpace(curpos.row, curpos.col);
                        cd.register(car);
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
            while (carsatbridge >= limit){
                wait();
            }
            if (CarWaiting && no > 4){
                while(CarWaiting || carsatbridge >= limit) {
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
            notifyAll();
            carsatbridge--;
        }

        public synchronized void leaving(){
            CarWaiting = false;
            notifyAll();
        }

        public synchronized void setLimit(int k){
            if (k > limit) {
                for ( int i = 0 ; i < k-limit ; i++){
                    notify();
                }
            }
            this.limit = k;
        }
    public void initRemoveCarBooleans(Boolean[] removeCarBoolean) {
        for (int i = 0; i <= 8; i++)
            removeCarBoolean[i] = false;
    }

    public void initRestoreCarBooleans(Boolean[] restoreCarBoolean) {
        for (int i = 0; i <= 8; i++)
            restoreCarBoolean[i] = false;
    }

    class Barrier {

        boolean barrierActivated = false;
        boolean barrierShutDown = false;
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

        public synchronized void shutDown() {
            barrierShutDown = true;
            if(barrierActivated) {
                try {
                    wait();
                    barrierShutDown = false;
                    off();
                } catch (Exception e) {
                    System.out.println(e);
                }
            } else {
                cd.println("Barrier is not activated");
            }
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
                    while(!HigherPassageAllowed && !removeCarBoolean[no]) {
                        wait();
                    }
                } else {
                    oneWaiting = true;
                    while(!HigherPassageAllowed && !removeCarBoolean[no]) {
                        wait();
                    }
                }
                if(!removeCarBoolean[no]) {
                    HigherPassageAllowed = true;
                    LowerPassageAllowed = false;
                }
                oneWaiting = false;

            }
            else if (HigherPassageAllowed && no>=5) {
                while(!LowerPassageAllowed && !removeCarBoolean[no]) {
                    wait();
                }
                if(!removeCarBoolean[no]) {
                    HigherPassageAllowed = false;
                    LowerPassageAllowed = true;
                }
            }
            if(!removeCarBoolean[no]) {
                carsInValley++;
            }
        }

        public synchronized void leave(int no) throws InterruptedException {
            carsInValley--;
            if(carsInValley==0) {
                LowerPassageAllowed = true;
                HigherPassageAllowed = true;
                notifyAll();
            }

        }

        public synchronized void wakeUpForRemoval() {
            notifyAll();
        }
    }

    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] sFields = new Semaphore[11][12];
    Semaphore[] barrierSemaphore = new Semaphore[9];
    Boolean[] removeCarBoolean = new Boolean[9];
    Boolean[] restoreCarBoolean = new Boolean[9];
    Alley alleysync = new Alley();
    Barrier barrier = new Barrier();
    Bridge bridge = new Bridge();

    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];

        initSemFields(sFields);
        initBarrierSemaphore(barrierSemaphore);
        initRemoveCarBooleans(removeCarBoolean);
        initRestoreCarBooleans(restoreCarBoolean);

        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], sFields, alleysync, barrier, removeCarBoolean, restoreCarBoolean,bridge);
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
        barrier.shutDown();
    }

    public void removeCar(int no) {
        conductor[no].removeCar(no);
    }

    public void restoreCar(int no) {
        conductor[no].restoreCar(no);
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) {
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) {
        conductor[no].setVariation(var);
    }

}






