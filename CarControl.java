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
    Semaphore[][] sFields;
    Alley alley = new Alley();

    class Alley {

        public void enter(int no) {
        }
        public void leave(int no) {
        }
    }

    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] sFields) {

        this.no = no;
        this.cd = cd;
        this.sFields = sFields;
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

    public void takeSpace(int row, int col) throws InterruptedException {
        sFields[row][col].P();
    }
    public void freeSpace(int row, int col) {
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

                takeSpace(newpos.row, newpos.col);

                car.driveTo(newpos);
//                    alley.enter(no);

                freeSpace(curpos.row, curpos.col);

                curpos = newpos;
            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }

}

public class CarControl implements CarControlI{

    public void initSemFields(Semaphore[][] sFields) {
        for (int i = 0; i < 11; i++)
            for (int j = 0; j < 12; j++)
                sFields[i][j] = new Semaphore(1);

    }

    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] sFields = new Semaphore[11][12];

    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];

        initSemFields(sFields);

        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], sFields);
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
        cd.println("Barrier On not implemented in this version");
    }

    public void barrierOff() {
        cd.println("Barrier Off not implemented in this version");
    }

    public void setLimit(int k) {
        cd.println("Setting of bridge limit not implemented in this version");
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






