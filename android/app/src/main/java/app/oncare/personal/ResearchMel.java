package app.oncare.personal;

/** APSAA experiment v1 only: mono 4kHz, 30s, Hann(100), hop40, FFT128. */
final class ResearchMel {
    static final int SAMPLES=120000, FRAMES=2998;
    private static final double[] WINDOW=new double[100];
    private static final double[][] BANK=new double[64][65];
    static {
        for(int i=0;i<100;i++)WINDOW[i]=(float)(.5-.5*Math.cos(2*Math.PI*i/99));
        double lo=2595*Math.log10(1+80.0/700),hi=2595*Math.log10(1+1900.0/700);
        double[] edges=new double[66];for(int i=0;i<66;i++)edges[i]=700*(Math.pow(10,(lo+(hi-lo)*i/65)/2595)-1);
        for(int m=0;m<64;m++)for(int k=0;k<65;k++)BANK[m][k]=(float)Math.max(0,Math.min((k*31.25-edges[m])/(edges[m+1]-edges[m]),(edges[m+2]-k*31.25)/(edges[m+2]-edges[m+1])));
    }
    static float[] extract(float[] samples){
        if(samples.length!=SAMPLES)throw new IllegalArgumentException("30 seconds required");
        for(float v:samples)if(!Float.isFinite(v))throw new IllegalArgumentException("Invalid PCM");
        float[] mel=new float[64*FRAMES];double[] re=new double[128],im=new double[128],power=new double[65];
        for(int frame=0;frame<FRAMES;frame++){
            java.util.Arrays.fill(re,0);java.util.Arrays.fill(im,0);
            for(int i=0;i<100;i++)re[i]=(float)(samples[frame*40+i]*WINDOW[i]);
            fft(re,im);for(int k=0;k<65;k++)power[k]=re[k]*re[k]+im[k]*im[k];
            for(int m=0;m<64;m++){double sum=0;for(int k=0;k<65;k++)sum+=BANK[m][k]*power[k];mel[m*FRAMES+frame]=(float)Math.log(Math.max(sum,1e-10));}
        }return mel;
    }
    private static void fft(double[] re,double[] im){
        for(int i=1,j=0;i<128;i++){int bit=64;for(;(j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=re[i];re[i]=re[j];re[j]=t;}}
        for(int size=2;size<=128;size*=2){double angle=-2*Math.PI/size,wr=Math.cos(angle),wi=Math.sin(angle);
            for(int start=0;start<128;start+=size){double ar=1,ai=0;for(int j=0;j<size/2;j++){int a=start+j,b=a+size/2;double br=re[b]*ar-im[b]*ai,bi=re[b]*ai+im[b]*ar;re[b]=re[a]-br;im[b]=im[a]-bi;re[a]+=br;im[a]+=bi;double next=ar*wr-ai*wi;ai=ar*wi+ai*wr;ar=next;}}}
    }
    /** Stateful anti-alias FIR, integer decimation 16k -> 4k, reset per session. */
    static final class Decimator {
        private final double[] taps=new double[65],history=new double[65];private int pos=0,phase=0;
        Decimator(){double total=0;for(int i=0;i<65;i++){double x=i-32,cutoff=1800.0/16000;taps[i]=(x==0?2*cutoff:Math.sin(2*Math.PI*cutoff*x)/(Math.PI*x))*(.54-.46*Math.cos(2*Math.PI*i/64));total+=taps[i];}for(int i=0;i<65;i++)taps[i]/=total;}
        float add(short input){history[pos]=input/32768.0;pos=(pos+1)%65;phase=(phase+1)%4;if(phase!=0)return Float.NaN;double value=0;for(int k=0;k<65;k++)value+=taps[k]*history[(pos-1-k+130)%65];return(float)value;}
    }
}
