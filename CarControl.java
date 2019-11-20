//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2017

//Hans Henrik Lovengreen       Oct 8, 2019

import java.awt.Color;

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
    Boolean[][] sFields;
    CarControl.Alley alley;
    CarControl.Barrier barrier;
    Boolean[] removeCarBoolean;
    Boolean[] restoreCarBoolean;
    CarControl.removedSync removesync;
    CarControl.fieldSync fieldsync;
    boolean[] removeCarGateOpen = new boolean[9];


    public Conductor(int no, CarDisplayI cd, Gate g, Boolean[][] sFields, CarControl.Alley alley,
                     CarControl.Barrier barrier, Boolean[] removeCarBoolean, Boolean[] restoreCarBoolean,
                     CarControl.removedSync removesync, CarControl.fieldSync fieldsync) {

        this.no = no;
        this.cd = cd;
        this.sFields = sFields;
        this.alley = alley;
        this.barrier = barrier;
        this.removeCarBoolean = removeCarBoolean;
        this.restoreCarBoolean = restoreCarBoolean;
        this.removesync = removesync;
        this.fieldsync = fieldsync;
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

    public synchronized void removeTheCar() {
        // check if car already been removed
        if(!removeCarBoolean[no]) {
            removeCarBoolean[no] = true;
            try {
                // if waiting at gate open gate so thread is released
                if(atGate(curpos) && !mygate.isopen) {
                    // to mark that gate was opened here
                    removeCarGateOpen[no] = true;
                    mygate.open();
                }
                // trying to wake up thread if waiting at alley or for a field to be free
                alley.wakeUpForRemoval();
                fieldsync.wakeUpForRemoval();
            } catch (Exception e) {
                cd.println("Exception in Car no. " + no);
            }
        } else {
            cd.println("Car already removed");
        }
    }
    public void restoreTheCar(int no) {
        // check if car is already present
        if(removeCarBoolean[no]) {
                restoreCarBoolean[no] = true;
                try {
                    // wake up sleeping thread
                    removesync.wakeUp(no);
                } catch (Exception e) {
                    System.out.println(e);
                }
        } else {
            cd.println("Car already registered");
        }
    }

    public void run() {
        try {
            CarI car = cd.newCar(no, col, startpos);
            curpos = startpos;
            cd.register(car);
            // to check later if car has been removed
            boolean hasBeenRemoved = false;
            // local thread boolean to know which state car was in at time of removal
            boolean removedWhileWaitingForTile = false;
            boolean inAlley = false;
            boolean removedWhileWaitingGate = false;

            while (true) {
                    if (!removeCarBoolean[no]) {
                        if (atGate(curpos)) {
                            mygate.pass();
                            // if gate was opened by removeCar method to wake thread up
                            if(removeCarBoolean[no] && removeCarGateOpen[no]) {
                                removedWhileWaitingGate = true;
                            }
                            car.setSpeed(chooseSpeed());
                        }

                        newpos = nextPos(curpos);

                        fieldsync.takeField(no, newpos.row, newpos.col);

                        // if the car has been woken up from takeSpace to be removed it is supposed to skip rest of this segment
                        if (!removeCarBoolean[no]) {
                            car.driveTo(newpos);

                            fieldsync.leaveField(curpos.row, curpos.col);

                            if ((newpos.row == 10 && newpos.col == 0) || (newpos.row == 2 && newpos.col == 1) || (newpos.row == 1 && newpos.col == 3)) {
                                alley.enter(no);
                                // if car was removed while in the alley this is noted
                                if (!removeCarBoolean[no]) {
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
                                    barrier.sync();
                                }
                            }

                            curpos = newpos;
                        } else {
                            removedWhileWaitingForTile = true;
                        }
                    } else {
                        // check if car has been removed yet
                        if (!hasBeenRemoved) {
                            cd.deregister(car);
                            // leave alley if car was in alley at time of removal
                            if (inAlley) {
                                alley.leave(no);
                            }
                            if (removedWhileWaitingForTile) {
                                fieldsync.leaveField(curpos.row, curpos.col);
                                removedWhileWaitingForTile = false;
                            } else {
                                fieldsync.leaveField(curpos.row, curpos.col);
                            }
                            hasBeenRemoved = true;
                            // close the gate if gate was opened due to car being removed
                            if(removedWhileWaitingGate) {
                                mygate.close();
                                removedWhileWaitingGate = false;
                            }
                            // put thread to wait until it is being restored
                            if(!restoreCarBoolean[no]) {
                                removesync.sleep(no);
                            }
                        }
                        if (restoreCarBoolean[no]) {
                            // no longer being removed or restored so booleans is set to false
                            removeCarBoolean[no] = false;
                            hasBeenRemoved = false;
                            restoreCarBoolean[no] = false;
                            // making new car to start at gate
                            car = cd.newCar(no, col, startpos);
                            curpos = startpos;
                            // take start field
                            fieldsync.takeField(no, curpos.row, curpos.col);
                            // register car
                            cd.register(car);
                        }
                    }
            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }
}

public class CarControl implements CarControlI{

    public void initSemFields(Boolean[][] sFields) {
        for (int i = 0; i < 11; i++)
            for (int j = 0; j < 12; j++)
                sFields[i][j] = true;
    }

    public void initBarrierSemaphore(Semaphore[] barrierSemaphore) {
        for (int i = 0; i <= 8; i++)
            barrierSemaphore[i] = new Semaphore(0);
    }

    public void initRemoveCarBooleans(Boolean[] removeCarBoolean) {
        for (int i = 0; i <= 8; i++)
            removeCarBoolean[i] = false;
    }

    public void initRestoreCarBooleans(Boolean[] restoreCarBoolean) {
        for (int i = 0; i <= 8; i++)
            restoreCarBoolean[i] = false;
    }

    class fieldSync {

        public synchronized void takeField(int no, int row, int col) {
            try {
                if(!sFields[row][col]) {
                    while(!sFields[row][col] && !removeCarBoolean[no]) {
                        wait();
                    }
                }
                if(!removeCarBoolean[no]) {
                    sFields[row][col] = false;
                }
            } catch (Exception e) {
                System.out.println("Exception in fieldSync: " + e);
            }
        }

        public synchronized void leaveField(int row, int col) {
            try {
                sFields[row][col] = true;
                notifyAll();
            } catch (Exception e) {
                System.out.println("Exception in fieldSync: " + e);
            }
        }

        public synchronized void wakeUpForRemoval() {
            notifyAll();
        }
    }

    class Barrier {
        boolean barrierActivated = false;
        boolean barrierShutDown = false;
        boolean allowedToGo = false;
        int carsAtBarrier = 0;
        int carsLeft = 0;

        // Wait for others to arrive (if barrier active)
        public synchronized void sync() throws InterruptedException {
            if(carsLeft!=0 && carsAtBarrier==0) {
                while(carsLeft!=0) {
                    wait();
                }
            }
            if(carsAtBarrier==0) {
                allowedToGo=false;
            }
            carsAtBarrier++;
            if(carsAtBarrier==9) {
                carsAtBarrier=0;
                if(!barrierShutDown) {
                    allowedToGo=true;
                    notifyAll();
                } else {
                    allowedToGo=true;
                    barrierShutDown = false;
                    barrierActivated = false;
                    notifyAll();
                }
            } else {
                while(!allowedToGo) {
                    wait();
                }
            }
            carsLeft++;
            if(carsLeft==9) {
                carsLeft=0;
                notifyAll();
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
                    while(barrierShutDown) {
                        wait();
                    }
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

    class removedSync {
        // to mark if thread has been put to wait else in case of wakeup being called before sleep, wakeup has to wait for this
        boolean[] isSleeping = new boolean[9];

        public synchronized void sleep(int no) throws InterruptedException {
            isSleeping[no] = true;
            while(!restoreCarBoolean[no]){
                wait();
            }
            notifyAll();
        }
        public synchronized void wakeUp(int no) throws InterruptedException {
            if(!isSleeping[no]) {
                wait();
            }
            isSleeping[no] = false;
            notifyAll();
        }
    }

    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Boolean[][] sFields = new Boolean[11][12];
    Semaphore[] barrierSemaphore = new Semaphore[9];
    Boolean[] removeCarBoolean = new Boolean[9];
    Boolean[] restoreCarBoolean = new Boolean[9];
    Alley alleysync = new Alley();
    Barrier barrier = new Barrier();
    removedSync removedsync = new removedSync();
    fieldSync fieldsync = new fieldSync();

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
            conductor[no] = new Conductor(no,cd,gate[no], sFields, alleysync, barrier, removeCarBoolean, restoreCarBoolean, removedsync, fieldsync);
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
        cd.println("Setting of bridge limit not implemented in this version");
    }

    public void barrierShutDown() {
        barrier.shutDown();
    }

    public synchronized void removeCar(int no) {
        conductor[no].removeTheCar();
    }

    public void restoreCar(int no) {
        conductor[no].restoreTheCar(no);
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) {
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) {
        conductor[no].setVariation(var);
    }

}






