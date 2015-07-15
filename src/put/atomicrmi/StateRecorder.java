package put.atomicrmi;

public interface StateRecorder {

	void applyChanges(Object object) throws Exception;

}
