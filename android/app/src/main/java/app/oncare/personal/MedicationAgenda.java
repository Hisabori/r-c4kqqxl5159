package app.oncare.personal;
import java.util.*;
import java.text.Normalizer;

final class MedicationAgenda {
    private static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);}
    static boolean matches(MedicationStore.Plan plan,String query){String trimmed=query.trim();if(trimmed.isEmpty())return true;String text=normalized(plan.name+" "+plan.instructions);for(String word:trimmed.split("[\\s\\p{Z}]+"))if(!text.contains(normalized(word)))return false;return true;}
    static boolean overlaps(MedicationStore.Plan plan,List<MedicationStore.Plan> plans){
        String key=java.text.Normalizer.normalize(plan.name,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", "");if(key.isEmpty())return false;
        for(MedicationStore.Plan other:plans){String name=java.text.Normalizer.normalize(other.name,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", "");if(!other.id.equals(plan.id)&&key.equals(name)&&plan.hour==other.hour&&plan.minute==other.minute&&!Collections.disjoint(plan.days,other.days))return true;}return false;
    }
    static final class Summary {final int due;final MedicationStore.Plan next;Summary(int due,MedicationStore.Plan next){this.due=due;this.next=next;}}
    static Summary summary(List<MedicationStore.Plan> today,Set<String> checked,long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);int minute=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE),due=0;MedicationStore.Plan next=null;
        for(MedicationStore.Plan p:today){if(checked.contains(p.id))continue;int time=p.hour*60+p.minute;if(time<=minute)due++;else if(next==null||time<next.hour*60+next.minute||time==next.hour*60+next.minute&&p.id.compareTo(next.id)<0)next=p;}
        return new Summary(due,next);
    }
}
