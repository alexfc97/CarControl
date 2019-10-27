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
    CarControl.Alley alley;


    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] sFields, CarControl.Alley alley ) {

        this.no = no;
        this.cd = cd;
        this.sFields = sFields;
        this.alley = alley;
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
        if((row==10 && col==1) || (row==2 && col==2) || (row==1 && col==3)) {
            alley.enter(no);
        }
    }
    public void freeSpace(int row, int col, int no) {
        sFields[row][col].V();
        if (no<=4) {
            if(row==9 && col==0) {
                alley.leave(no);
            }
        }
        if (no>=5) {
            if(row==0 && col==2) {
                alley.leave(no);
            }
        }
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

    class Barrier {
        boolean barrieractivated = false;
        Semaphore barrierTickets = new Semaphore(8);

        public void sync() {  }  // Wait for others to arrive (if barrier active)

        public void on() {
            barrieractivated = true;
        }    // Activate barrier


        public void off() {
            boolean barrieractivated = false;
        }   // Deactivate barrier

    }

    class Alley {

        int carsInValley = 0;
        boolean upperAllowed = true;
        boolean lowerAllowed = true;
        Semaphore alley = new Semaphore(1);

        public void enter(int no) throws InterruptedException {
            if(upperAllowed && lowerAllowed) {
                alley.P();
                if(no<=4) {
                    upperAllowed = false;
                }
                else {
                    lowerAllowed = false;
                }
            }
            else if (upperAllowed && no<=4) {
                System.out.println("stuck in 1st: " + no);
                alley.P();

            }
            else if (lowerAllowed && no>=5) {
                System.out.println("stuck in 2nd " + no);
                alley.P();
            }
            carsInValley++;

        }
        public void leave(int no) {
            carsInValley--;
            if(carsInValley==0) {
                alley.V();
                upperAllowed = true;
                lowerAllowed = true;
            }
        }
    }

    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] sFields = new Semaphore[11][12];
    Alley alleysync = new Alley();

    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];

        initSemFields(sFields);

        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], sFields, alleysync);
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






