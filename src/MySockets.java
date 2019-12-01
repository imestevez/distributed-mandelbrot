
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author ivan
 */
public class MySockets {

    public static void main(String args[]) {
        CountDownLatch doneSignal = new CountDownLatch(1);
        try {
            new Server(25, doneSignal).start();
        } catch (IOException ex) {
            Logger.getLogger(MySockets.class.getName()).log(Level.SEVERE, null, ex);
        }
        //Espera que termine el Server (ultimo Thread en acabar su trabajo)
        try {
            doneSignal.await();           // wait for all to finish
        } catch (InterruptedException ex) {
            Logger.getLogger(MySockets.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("\nProgram of exercise P4 has terminated");
    }
}

/**
 * *******************************************************************
 * *******************************************************************
 * @author ivan
 * ********************************************************************
 * *********************************************************************
 */
class Server extends Thread {

    private final CountDownLatch doneSignal; //signal para saber cuando termina el hilo
    Gray bufferedImage;
    Socket socket = null;
    ObjectInputStream ois = null;
    ObjectOutputStream oos = null;
    int celdas;
    File file_in;
    Client clientes[];
    int i = 0, j = 0;
    ServerSocket server = new ServerSocket(4444);
    int[][] suma;

    public Server(int c, CountDownLatch doneSignal) throws IOException {
        this.doneSignal = doneSignal;
        file_in = new File("image.png");
        this.bufferedImage = new Gray(file_in);
        bufferedImage.input();
        clientes = new Client[c];

        CountDownLatch startSignal = new CountDownLatch(1);
        //señal de finalizacion inicializada a 25 (num clientes)
        CountDownLatch doneSignalClient = new CountDownLatch(c);

        for (int x = 0; x < clientes.length; x++) {
            clientes[x] = new Client(x, startSignal, doneSignalClient);
            clientes[x].start();
        }
        //Decrementa el valor de la señal para que arranquen los hilos clientes
        startSignal.countDown();
        celdas = 225 / 5;
    }

    public void run() {
        try {
            for (int x = 0; x < clientes.length; x++) {
                socket = server.accept();
                //Objeto de entrada
                ois = new ObjectInputStream(socket.getInputStream());
                String message = (String) ois.readObject();
                System.out.println("Server Received: " + message);
                //Objeto de salida
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject("Server Reply");
                if (i < 225) {
                    oos.writeObject(bufferedImage); //Objeto tipo Gray
                    oos.writeObject(i); //fila
                    oos.writeObject(j);//columna
                    oos.writeObject(celdas);//celdas a
                    //Recibe la porcion calculada por un cliente
                    suma = (int[][]) ois.readObject();
                    System.out.println("Server Received: region");
                    System.out.println("Server Received: region " + x);
                    bufferedImage.output(i, j, celdas, suma);
                    this.calculoCeldas(celdas);
                }
                ois.close();
            }
            oos.close();
            socket.close();

        } catch (Exception e) {
            System.err.println("Exception on Server");
        } finally {
            doneSignal.countDown();
        }
    }

    public void calculoCeldas(int celdas) {
        j += celdas;
        if (j >= 224) {
            i += celdas;
            j = 0;
        }
    }
}

/**
 * *******************************************************************
 * *******************************************************************
 * @author ivan
 * ********************************************************************
 * *********************************************************************
 */
class Client extends Thread {

    private final CountDownLatch startSignal;
    private final CountDownLatch doneSignal;

    InetAddress host = null;
    Socket socket = null;
    ObjectOutputStream oos = null;
    ObjectInputStream ois = null;
    Gray img;
    int x;

    public Client(int num, CountDownLatch startSignal, CountDownLatch doneSignal) {
        x = num;
        this.startSignal = startSignal;
        this.doneSignal = doneSignal;
    }

    public void run() {

        try {
            //System.out.println("Client: " + x);
            host = InetAddress.getLocalHost();
            socket = new Socket(host.getHostName(), 4444);
            //Objeto de salida
            oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject("Client Message " + x);
            //Objeto de entrada
            ois = new ObjectInputStream(socket.getInputStream());
            String message1 = (String) ois.readObject();
            System.out.println("Client Received: " + message1);
            img = (Gray) ois.readObject();
            System.out.println("Client Received: Image");
            int i = (int) ois.readObject();
            System.out.println("Client Received: " + i + " fila origen");
            int j = (int) ois.readObject();
            System.out.println("Client Received: " + j + " columna origen");
            int celdas = (int) ois.readObject();
            System.out.println("Client Received: " + celdas + " celdas");
            int filtrado[][] = calculate(i, j, celdas, x);
            oos.writeObject(filtrado);
            System.out.println("Client Sent: " + " filtro");

            ois.close();
            oos.close();
            socket.close();
        } catch (Exception e) {
            System.err.println("Exception on Client" + x);

        } finally {
            doneSignal.countDown();
        }
    }

    public int[][] calculate(int k, int l, int celdas, int x) {
        int dimension = celdas * 5;
        int region[][] = new int[dimension][dimension];
        int i;
        int j = l;
        for (i = k; i < k + celdas; i++) {
            for (j = l; j < l + celdas; j++) {
                region[i][j] = iteraciones(((i - 225 / 2f)-(225/3.5f))/100, (j - 225 / 2f)/100);
            }
        }
        System.out.println("calculate " + x + " " + k + "  " + l + " to" + " " + (i - 1) + "  " + (j - 1));

        return region;
    }

    public int iteraciones(float x, float y) {
        int count = 0;
        float cx = x;
        float cy = y;
        for (; count < 100; count++) {
            float nx = x * x - y * y + cx;
            float ny = 2 * x * y + cy;
            x = nx;
            y = ny;
            
            if(x*x + y*y > 4)break;
        }
        if(count == 100)return 0x00000000;
        return 0xFFFFFFF;
    }
    /*
    public int filtroMediano(int fila, int columna) {
        int suma = 0;
        int auxF = 0, auxC = 0; //Coge las celdas de los vecinos de (fila,columna)
        for (int f = fila - 1; f < fila + 2; f++) { //recorre las filas vecinas
            for (int c = columna - 1; c < columna + 2; c++) { //reorre las columnas vecinas
                if (f < 0 || f > img.data.length - 1) { //si la fila supera sobrepasa el borde
                    auxF = fila + (fila - f); //Coge la fila reflejo
                } else {
                    auxF = f;
                }
                if (c < 0 || c > img.data[0].length - 1) { //si la columna sobrepasa el borde
                    auxC = columna + (columna - c); //coge la columna reflejo
                } else {
                    auxC = c;
                }

                suma += img.data[auxF][auxC]; //incrementa la variable suma con el valor de los vecinos
            }
        }
        return suma/9;
    }
     */
}

/**
 * *******************************************************************
 * *******************************************************************
 * @author ivan
 * ********************************************************************
 * *********************************************************************
 */
class Gray implements Serializable {

    static BufferedImage img;
    int width, height;
    int[][] data;

    public Gray(File file_in) throws IOException {
        img = ImageIO.read(file_in);
        width = img.getWidth();
        height = img.getHeight();
        data = new int[width][height];
    }

    public void input() {
        try {
            Raster raster_in = img.getData();
            for (int i = 0; i < height; i++) {
                for (int j = 0; j < width; j++) {
                    final int d = raster_in.getSample(i, j, 0);
                    data[i][j] = d;
                }
            }
        } catch (Exception E) {
        }
    }

    public void output(int k, int l, int celdas, int[][] region) {
        // System.err.println("In: "+k + " " + l + " " + x);
        WritableRaster raster_out = img.getRaster();
        int i, j = 0;
        for (i = k; i < k + celdas; i++) {
            for (j = l; j < l + celdas; j++) {
                raster_out.setSample(i, j, 0, region[i][j] / 2);
            }
        }
        //System.err.println("out: "+i + " " + j + " " + x);

        img.setData(raster_out);
        File file_out = new File("out.png");
        try {
            ImageIO.write(img, "png", file_out);
        } catch (IOException ex) {
            Logger.getLogger(Gray.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
