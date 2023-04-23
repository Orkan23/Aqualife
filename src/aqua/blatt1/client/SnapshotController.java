package aqua.blatt1.client;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SnapshotController implements ActionListener {
    AquaGui aquaGui;
    TankModel tankModel;

    public SnapshotController(AquaGui aquaGui, TankModel tankModel) {
        this.aquaGui = aquaGui;
        this.tankModel = tankModel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        tankModel.initiateSnapshot();
    }
}
