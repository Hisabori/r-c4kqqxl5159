package app.oncare.personal;
import org.junit.Test;
import static org.junit.Assert.*;
public class ResearchMelTest {
    @Test public void silenceHasFiniteLogFloor(){float[] result=ResearchMel.extract(new float[120000]);assertEquals(64*2998,result.length);for(float value:result)assertEquals(Math.log(1e-10),value,1e-5);}
    @Test public void invalidEpochIsRejected(){try{ResearchMel.extract(new float[1]);fail();}catch(IllegalArgumentException expected){}float[] a=new float[120000];a[5]=Float.NaN;try{ResearchMel.extract(a);fail();}catch(IllegalArgumentException expected){}}
    @Test public void decimatorSuppressesAliasing(){double low=power(500),high=power(5000);assertTrue(low>0.1);assertTrue(high<low*.001);}
    private double power(double hz){ResearchMel.Decimator filter=new ResearchMel.Decimator();double sum=0;int n=0;for(int i=0;i<16000;i++){float v=filter.add((short)(25000*Math.sin(2*Math.PI*hz*i/16000)));if(!Float.isNaN(v)&&i>200){sum+=v*v;n++;}}return sum/n;}
}
