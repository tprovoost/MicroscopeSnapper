package plugins.tprovoost.Microscopy.MicroscopeSnapper;

import icy.gui.frame.IcyFrameAdapter;
import icy.gui.frame.IcyFrameEvent;

import org.micromanager.utils.StateItem;

import plugins.tprovoost.Microscopy.MicroManagerForIcy.MicroscopePluginAcquisition;

public class MicroscopeSnapperPlugin extends MicroscopePluginAcquisition {

	private MicroSnapperFrame _frame;
		
	@Override
	public void start() {
		// create the frame of the plugin with a separated class
		_frame = new MicroSnapperFrame(this);
		
		// add the plugin to the GUI
		mainGui.addPlugin(this);
		
		// add a listener to the frame in order to remove the plugin from
		// the GUI when the frame is closed
		_frame.addFrameListener(new IcyFrameAdapter() {
			@Override
			public void icyFrameClosed(IcyFrameEvent e) {
				super.icyFrameClosed(e);
				mainGui.removePlugin(MicroscopeSnapperPlugin.this);
			}
		});
	}

	@Override
	public void notifyConfigAboutToChange(StateItem item) {}

	@Override
	public void notifyConfigChanged(StateItem item) {}

	@Override
	public void MainGUIClosed() {
		_frame.close();
	}

	@Override
	public String getRenderedName() {
		return "Microscope Snapper";
	}

}
