package plugins.tprovoost.Microscopy.MicroscopeSnapper;

import icy.gui.component.IcyLogo;
import icy.gui.component.button.IcyButton;
import icy.gui.dialog.ConfirmDialog;
import icy.gui.dialog.MessageDialog;
import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.AnnounceFrame;
import icy.gui.main.MainAdapter;
import icy.gui.main.MainEvent;
import icy.gui.main.MainListener;
import icy.gui.util.GuiUtil;
import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.main.Icy;
import icy.resource.icon.IcyIcon;
import icy.sequence.Sequence;
import icy.sequence.VolumetricImage;
import icy.system.thread.ThreadUtil;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Calendar;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import plugins.tprovoost.Microscopy.MicroManagerForIcy.MicroscopeCore;
import plugins.tprovoost.Microscopy.MicroManagerForIcy.MicroscopePluginAcquisition;
import plugins.tprovoost.Microscopy.MicroManagerForIcy.MicroscopeSequence;
import plugins.tprovoost.Microscopy.MicroManagerForIcy.Tools.ImageGetter;
import plugins.tprovoost.Microscopy.MicroManagerForIcy.Tools.StageMover;

public class MicroSnapperFrame extends IcyFrame {

	private static enum EnumTypeSnap {
		SNAPZ, SNAPT, SNAPC;
	}

	/** Reference to owner */
	MicroscopeSnapperPlugin plugin;

	// --------------
	// GUI Components
	// ---------------
	private IcyButton _btn_snap;
	private IcyButton _btn_createSnap;
	private IcyButton _btn_lock_sequence;
	private Sequence _locked_sequence = null;
	private Snap3DPanel _panel_3D;

	/** Is the snapper already capturing images ? */
	boolean capturingImages = false;

	public MicroSnapperFrame(MicroscopeSnapperPlugin plugin) {
		super("", true, true, false, true);
		this.plugin = plugin;

		// --------------
		// Set up Panels
		// --------------
		JPanel panel_2D = new JPanel();
		panel_2D.setLayout(new GridLayout());

		_panel_3D = new Snap3DPanel();

		// --------------
		// LOGO CREATION
		// --------------
		IcyLogo _logo = new IcyLogo("Snapper");
		_logo.setPreferredSize(new Dimension(100, 70));
		add(_logo, "North");

		// -----------------
		// BUTTON CREATION
		// -----------------
		_btn_snap = new IcyButton(new IcyIcon("layers_2.png"));
		_btn_snap.setHorizontalAlignment(JButton.CENTER);
		_btn_snap.setToolTipText("Capture and add to the focused sequence as new position");
		_btn_snap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				snapandAdd();
			}
		});
		_btn_snap.setEnabled(Icy.getMainInterface().getFocusedSequence() != null);

		_btn_createSnap = new IcyButton(new IcyIcon("duplicate.png"));
		_btn_createSnap.setHorizontalAlignment(JButton.CENTER);
		_btn_createSnap.setToolTipText("Create a new sequence from snapped image.");
		_btn_createSnap.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				createAndSnap();
			}
		});

		// Button used to force snapped images to be added the sequence locked
		// instead of current sequence
		_btn_lock_sequence = new IcyButton(new IcyIcon("padlock_open.png"));
		_btn_lock_sequence.setToolTipText("Instead of being added to the focused sequence, images will be snapped to the locked one.");
		_btn_lock_sequence.setHorizontalAlignment(JButton.CENTER);
		_btn_lock_sequence.setEnabled(Icy.getMainInterface().getFocusedSequence() != null);
		_btn_lock_sequence.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (_locked_sequence == null) {
					_locked_sequence = Icy.getMainInterface().getFocusedSequence();
					_btn_lock_sequence.setIcon(new IcyIcon("padlock_closed.png"));
					_btn_lock_sequence.setToolTipText("Unlock the sequence, future snaps will be on focused sequence.");
				} else {
					_locked_sequence = null;
					_btn_lock_sequence.setIcon(new IcyIcon("padlock_open.png"));
					_btn_lock_sequence
							.setToolTipText("Instead of being added to the focused sequence, images will be snapped to the locked one.");
				}
			}
		});
		JPanel panel_buttons = new JPanel();
		panel_buttons.setLayout(new GridLayout(1, 3));
		panel_buttons.add(_btn_createSnap);
		panel_buttons.add(_btn_snap);
		panel_buttons.add(_btn_lock_sequence);

		// Creation of the panel
		JPanel panel_middle = new JPanel();
		panel_middle.setLayout(new BoxLayout(panel_middle, 1));
		panel_middle.add(_panel_3D);
		panel_middle.add(panel_buttons);
		add(panel_middle, "Center");
		setVisible(true);

		// add the frame to Icy.
		addToMainDesktopPane();

		// add a listener.
		MainListener listener = new MainAdapter() {
			public void sequenceOpened(MainEvent event) {
				if (!_btn_snap.isEnabled()) {
					_btn_snap.setEnabled(true);
					_btn_lock_sequence.setEnabled(true);
				}
			}

			public void sequenceClosed(MainEvent event) {
				if (Icy.getMainInterface().getSequences().size() == 0) {
					_btn_snap.setEnabled(false);
					_btn_lock_sequence.setEnabled(false);
				}
			}
		};
		Icy.getMainInterface().addListener(listener);
		requestFocus();
		center();
		pack();
	}

	void snapandAdd() {
		final Sequence currentSequence;
		if (_locked_sequence != null)
			currentSequence = _locked_sequence;
		else
			currentSequence = Icy.getMainInterface().getFocusedSequence();
		if (!_panel_3D.isRunning())
			ThreadUtil.bgRun(new Runnable() {
				@Override
				public void run() {
					_panel_3D.action(currentSequence);
				}
			});
	}

	void createAndSnap() {
		ThreadUtil.bgRun(new Runnable() {
			@Override
			public void run() {
				_panel_3D.action(null);
			}
		});
	}

	public class Snap3DPanel extends JPanel implements ActionListener {

		private EnumTypeSnap typeAction = EnumTypeSnap.SNAPT;

		/** Generated serial ID */
		private static final long serialVersionUID = 4587083653276086917L;

		/** CoreSingleton instance */
		MicroscopeCore core;

		private int _slices = 10;
		private double _interval_ = 1.0D;

		private JRadioButton _rb_snap_z;
		private JRadioButton _rb_snap_t;
		private JRadioButton _rb_snap_c;

		private JScrollBar _scrollbar_slices;

		private JPanel _panel_scroll;
		private JPanel panel_3d_options;

		JLabel _lbl_slices_above;
		JLabel _lbl_slices_below;

		private boolean isRunning = false;

		public Snap3DPanel() {
			core = MicroscopeCore.getCore();

			// ----------------
			// SNAP OPTIONS
			// ---------------
			JPanel panel_snap_mode = GuiUtil.generatePanel("Snap options");
			panel_snap_mode.setLayout(new GridLayout(3, 1));

			_rb_snap_z = new JRadioButton("Snap to Z");
			_rb_snap_z.setToolTipText("When hitting \"snap\", one image will be added to the Z-Dimension of the current sequence.");
			_rb_snap_z.setActionCommand("snapz");
			_rb_snap_z.addActionListener(this);

			_rb_snap_t = new JRadioButton("Snap to T");
			_rb_snap_t.setToolTipText("When hitting \"snap\", slices will be added to the T-Dimension of the current sequence.");
			_rb_snap_t.setActionCommand("snapt");
			_rb_snap_t.addActionListener(this);

			_rb_snap_c = new JRadioButton("Snap to C");
			_rb_snap_c.setToolTipText("When hitting \"snap\", slices will be added as a new channel of the current sequence.");
			_rb_snap_c.setActionCommand("snapc");
			_rb_snap_c.addActionListener(this);

			ButtonGroup group = new ButtonGroup();
			group.add(_rb_snap_z);
			group.add(_rb_snap_t);
			group.add(_rb_snap_c);

			// ----------------
			// SLICES OPTIONS
			// ---------------
			panel_3d_options = GuiUtil.generatePanel("Slices options");
			panel_3d_options.setLayout(new GridLayout(2, 1));
			JPanel panel_slices = new JPanel();
			JPanel panel_interval = new JPanel();

			panel_slices.setLayout(new BoxLayout(panel_slices, BoxLayout.X_AXIS));
			panel_interval.setLayout(new BoxLayout(panel_interval, BoxLayout.X_AXIS));

			final SpinnerNumberModel model_slices = new SpinnerNumberModel(10, 1, 1000, 1);
			final JSpinner _spin_slices = new JSpinner(model_slices);
			model_slices.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent changeevent) {
					_slices = model_slices.getNumber().intValue();
					if (_slices <= 1) {
						setPanelEnabled(_panel_scroll, false);
					} else if (!_panel_scroll.isEnabled())
						setPanelEnabled(_panel_scroll, true);
					_scrollbar_slices.setValues(_slices / 2, _slices, 0, _slices * 2);
					setScrollBarText();
				}
			});

			final JTextField _tf_interval = new JTextField("1.0", 4);
			_tf_interval.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent keyevent) {
					super.keyPressed(keyevent);
					if (keyevent.getKeyCode() == KeyEvent.VK_ENTER)
						try {
							_interval_ = Double.valueOf(_tf_interval.getText()).doubleValue();
						} catch (NumberFormatException e) {
						}
				}
			});
			panel_slices.add(new JLabel("Slices Count: "));
			panel_slices.add(_spin_slices);
			panel_interval.add(new JLabel("Interval (µm): "));
			panel_interval.add(_tf_interval);

			panel_snap_mode.add(_rb_snap_z);
			panel_snap_mode.add(_rb_snap_t);
			panel_snap_mode.add(_rb_snap_c);
			panel_3d_options.add(panel_slices);
			panel_3d_options.add(panel_interval);

			_panel_scroll = GuiUtil.generatePanel("Distribution");
			_panel_scroll.setToolTipText("Choose the way images are taken. Click on \"?\" Button for more information.");
			_panel_scroll.setLayout(new BorderLayout());

			_scrollbar_slices = new JScrollBar(SwingConstants.VERTICAL, _slices / 2, _slices, 0, _slices * 2);
			_scrollbar_slices.setPreferredSize(new Dimension(15, 100));
			_scrollbar_slices.addAdjustmentListener(new AdjustmentListener() {

				@Override
				public void adjustmentValueChanged(AdjustmentEvent adjustmentevent) {
					setScrollBarText();
				}
			});

			JButton btn_help = new JButton("?");
			btn_help.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent actionevent) {
					MessageDialog.showDialog("<html>Choose the way images are taken:<br/>" + ""
							+ "<ul><li>Knot at the bottom = from current Z to higher Zs</li>"
							+ "<li>Knot centered = snap half of images below and half above current Z</li>"
							+ "<li>Knot at the top = from current Z to lower Zs</li></ul></html>");
				}
			});

			JPanel panel_slider_bar = new JPanel();
			panel_slider_bar.setLayout(new BorderLayout());
			panel_slider_bar.add(_scrollbar_slices, BorderLayout.CENTER);

			_lbl_slices_above = new JLabel("" + _slices / 2 + " above");
			_lbl_slices_above.setHorizontalAlignment(SwingConstants.CENTER);
			_lbl_slices_above.setHorizontalTextPosition(SwingConstants.CENTER);
			_lbl_slices_below = new JLabel("" + _slices / 2 + " below");
			_lbl_slices_below.setHorizontalAlignment(SwingConstants.CENTER);
			_lbl_slices_below.setHorizontalTextPosition(SwingConstants.CENTER);

			panel_slider_bar.add(_lbl_slices_above, BorderLayout.NORTH);
			panel_slider_bar.add(_lbl_slices_below, BorderLayout.SOUTH);

			_panel_scroll.add(panel_slider_bar);
			_panel_scroll.add(btn_help, BorderLayout.SOUTH);

			// ----------------
			// DISPLAY
			// ----------------
			JPanel panel_left = new JPanel();
			panel_left.setLayout(new BoxLayout(panel_left, BoxLayout.Y_AXIS));
			JPanel panel_right = new JPanel(new GridLayout());

			setLayout(new BorderLayout());
			panel_left.add(panel_snap_mode);
			panel_left.add(panel_3d_options);
			panel_left.add(Box.createVerticalGlue());

			panel_right.add(_panel_scroll);

			add(panel_left, BorderLayout.CENTER);
			add(panel_right, BorderLayout.EAST);

			_rb_snap_z.doClick();
		}

		private void setScrollBarText() {
			_lbl_slices_above.setText("" + _scrollbar_slices.getValue() + " above");
			_lbl_slices_below.setText("" + (_slices - _scrollbar_slices.getValue()) + " below");
		}

		void action(final Sequence paramSeq) {
			String nameZ = core.getFocusDevice();
			if (nameZ == null || nameZ == "")
				return;

			if (paramSeq == null) {
				// ------------
				// NEW SEQUENCE
				// ------------
				if (typeAction == EnumTypeSnap.SNAPZ || _slices == 1) {
					IcyBufferedImage capturedImage;
					if (core.isSequenceRunning())
						capturedImage = ImageGetter.getImageFromLive(core);
					else
						capturedImage = ImageGetter.snapImage(core);
					if (capturedImage == null) {
						new AnnounceFrame("No image was captured");
						return;
					}
					MicroscopeSequence s = new MicroscopeSequence(capturedImage);
					Calendar calendar = Calendar.getInstance();
					Icy.addSequence(s);
					s.setName("" + calendar.get(Calendar.MONTH) + "_" + calendar.get(Calendar.DAY_OF_MONTH) + "_"
							+ calendar.get(Calendar.YEAR) + "-"
							+ calendar.get(Calendar.HOUR_OF_DAY) + "_" + calendar.get(Calendar.MINUTE) + "_"
							+ calendar.get(Calendar.SECOND));
				} else {
					plugin.notifyAcquisitionStarted(true);
					setRunningFlag(true);
					MicroscopeSequence s = new MicroscopeSequence();
					ArrayList<IcyBufferedImage> list = captureStacks();
					for (int i = 0; i < list.size(); ++i) {
						s.addImage(list.get(i));
						double progress = 1D * i / list.size() * 10;
						plugin.notifyProgress(90 + (int) (progress));
					}
					Icy.addSequence(s);
					Calendar calendar = Calendar.getInstance();
					s.setName("" + calendar.get(Calendar.MONTH) + "_" + calendar.get(Calendar.DAY_OF_MONTH) + "_"
							+ calendar.get(Calendar.YEAR) + "-"
							+ calendar.get(Calendar.HOUR_OF_DAY) + "_" + calendar.get(Calendar.MINUTE) + "_"
							+ calendar.get(Calendar.SECOND));
					while (s.getFirstViewer() == null)
						try {
							Thread.sleep(50);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					s.getFirstViewer().setZ(_scrollbar_slices.getValue());
					s.setPixelSizeZ(_interval_);
					setRunningFlag(false);
					plugin.notifyAcquisitionOver();
				}
			} else {
				// ---------------
				// ADD TO SEQUENCE
				// ---------------
				if (typeAction == EnumTypeSnap.SNAPZ) {
					IcyBufferedImage capturedImage;
					if (core.isSequenceRunning())
						capturedImage = ImageGetter.getImageFromLive(core);
					else
						capturedImage = ImageGetter.snapImage(core);
					if (capturedImage == null) {
						new AnnounceFrame("No image was captured");
						return;
					}
					try {
						paramSeq.addImage(((Viewer) paramSeq.getViewers().get(0)).getT(), capturedImage);
					} catch (IllegalArgumentException e) {
						String toAdd = "";
						if (paramSeq.getSizeC() > 0)
							toAdd = toAdd + ": impossible to capture images with a colored sequence. Only Snap C are possible.";
						new AnnounceFrame("This sequence is not compatible" + toAdd);
					}
				} else {
					plugin.notifyAcquisitionStarted(true);
					Sequence s = paramSeq;
					ArrayList<IcyBufferedImage> list;
					if (_slices == 1) {
						IcyBufferedImage capturedImage;
						if (core.isSequenceRunning())
							capturedImage = ImageGetter.getImageFromLive(core);
						else
							capturedImage = ImageGetter.snapImage(core);
						if (capturedImage == null) {
							new AnnounceFrame("No image was captured");
							return;
						}
						list = new ArrayList<IcyBufferedImage>();
						list.add(capturedImage);
					} else
						list = captureStacks();
					if (_interval_ != paramSeq.getPixelSizeZ()
							&& ConfirmDialog.confirm("Warning",
									"Interval between slices in current sequence is different from the interval "
											+ "you want for this capture. Do you want to create a new sequence instead ?")) {
						Sequence s1 = new MicroscopeSequence();
						for (int i = 0; i < list.size(); ++i) {
							s1.addImage(list.get(i));
							double progress = 1D * i / list.size() * 10;
							plugin.notifyProgress(90 + (int) (progress));
						}
						Icy.addSequence(s1);
						Calendar calendar = Calendar.getInstance();
						s1.setName("" + calendar.get(Calendar.MONTH) + "_" + calendar.get(Calendar.DAY_OF_MONTH) + "_"
								+ calendar.get(Calendar.YEAR) + "-"
								+ calendar.get(Calendar.HOUR_OF_DAY) + "_" + calendar.get(Calendar.MINUTE) + "_"
								+ calendar.get(Calendar.SECOND));
						while (s1.getFirstViewer() == null)
							try {
								Thread.sleep(50);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						s1.getFirstViewer().setZ(_scrollbar_slices.getValue());
						s1.setPixelSizeZ(_interval_);
						plugin.notifyAcquisitionOver();
					} else {
						if (typeAction == EnumTypeSnap.SNAPT) {
							// SNAP T
							s.addVolumetricImage();
							for (int i = 0; i < list.size(); ++i) {
								try {
									s.setImage(s.getSizeT() - 1, i, list.get(i));
								} catch (IllegalArgumentException e) {
									MessageDialog.showDialog("Error with snapping", "Image not compatible.");
								}
								double progress = 1D * i / list.size() * 10;
								plugin.notifyProgress(90 + (int) (progress));
							}
						} else {
							// SNAP C
							if (_slices != s.getSizeZ()) {
								MessageDialog
										.showDialog("Error number of stacks",
												"The number of stacks of the snap does not correspond to the number of stacks in the current sequence.");
								return;
							}
							Sequence tmp = s.getCopy();
							try {
								s.beginUpdate();
								s.removeAllImage();
								for (int t = 0; t < tmp.getSizeT(); ++t) {
									VolumetricImage tmpVolum = tmp.getVolumetricImage(t);
									for (int z = 0; z < tmp.getSizeZ(); ++z) {
										IcyBufferedImage imgActu = tmpVolum.getImage(z);
										IcyBufferedImage imgNew = new IcyBufferedImage(imgActu.getWidth(), imgActu.getHeight(),
												imgActu.getNumComponents() + 1,
												imgActu.getDataType_());
										for (int c = 0; c < imgActu.getSizeC(); ++c) {
											imgNew.setDataXYAsShort(c, imgActu.getDataXYAsShort(c));
										}
										imgNew.setDataXYAsShort(imgNew.getSizeC() - 1, list.get(z).getDataXYAsShort(0));
										s.setImage(t, z, imgNew);
									}
									double progress = 1D * t / tmp.getSizeT() * 10;
									plugin.notifyProgress(90 + (int) (progress));
								}
							} finally {
								s.endUpdate();
							}
						}
						plugin.notifyAcquisitionOver();
					}
				}
			}
		}

		/**
		 * Capture the stack of images according to the parameters.
		 * 
		 * @return Returns an ArrayList containing all images.
		 */
		ArrayList<IcyBufferedImage> captureStacks() {
			String nameZ = core.getFocusDevice();
			ArrayList<IcyBufferedImage> list = new ArrayList<IcyBufferedImage>();
			double absoluteZ = 0;
			try {
				absoluteZ = core.getPosition(nameZ);
			} catch (Exception e1) {
				new AnnounceFrame("Error with focus device : position unknown");
				return list;
			}
			try {
				StageMover.moveZRelative(-((_slices - _scrollbar_slices.getValue()) * _interval_));
				core.waitForDevice(nameZ);
				if (core.isSequenceRunning()) {
					core.waitForExposure();
				}
				list.add(ImageGetter.snapImage(core));
			} catch (Exception e) {
				new AnnounceFrame("Error wile moving");
				return list;
			}
			for (int z = 1; z < _slices; ++z) {
				try {
					StageMover.moveZRelative(_interval_);
					if (core.isSequenceRunning()) {
						core.waitForExposure();
					}
					core.waitForImageSynchro();
					list.add(ImageGetter.snapImage(core));
				} catch (Exception e) {
					break;
				}
				double progress = 1D * z / _slices * 90D;
				plugin.notifyProgress((int) progress);
			}
			try {
				if (absoluteZ != 0)
					core.setPosition(nameZ, absoluteZ);
			} catch (Exception e) {
				new AnnounceFrame("Error while moving");
			}
			return list;
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			if (e.getActionCommand() == "snapz") {
				setPanelEnabled(_panel_scroll, false);
				setPanelEnabled(panel_3d_options, false);
				typeAction = EnumTypeSnap.SNAPZ;
			} else {
				if (!panel_3d_options.isEnabled()) {
					setPanelEnabled(panel_3d_options, true);
					if (_slices > 1)
						setPanelEnabled(_panel_scroll, true);
				}
				if (e.getActionCommand() == "snapc")
					typeAction = EnumTypeSnap.SNAPC;
				if (e.getActionCommand() == "snapt")
					typeAction = EnumTypeSnap.SNAPT;
			}
		}

		private void setPanelEnabled(JPanel panel, boolean enabled) {
			panel.setEnabled(enabled);
			for (Component c : panel.getComponents()) {
				if (c instanceof JPanel)
					setPanelEnabled((JPanel) c, enabled);
				else
					c.setEnabled(enabled);
			}
		}

		/**
		 * @return Return if thread is running.
		 */
		boolean isRunning() {
			return isRunning;
		}

		/**
		 * @param synchronized method in order to avoid concurrent access to the
		 *        capture
		 */
		synchronized void setRunningFlag(boolean isRunning) {
			this.isRunning = isRunning;
		}
	}

	public static ArrayList<IcyBufferedImage> captureStacks(MicroscopePluginAcquisition plugin, MicroscopeCore core, double zStart,
			double zStop, double step, boolean relative) {
		plugin.notifyAcquisitionStarted(true);
		int slices = (int) (Math.abs(zStop - zStart) / step);
		String nameZ = core.getFocusDevice();
		ArrayList<IcyBufferedImage> list = new ArrayList<IcyBufferedImage>();
		double absoluteZ = 0;
		try {
			absoluteZ = core.getPosition(nameZ);
		} catch (Exception e1) {
			new AnnounceFrame("Error with focus device : position unknown");
			return list;
		}
		try {
			if (relative)
				StageMover.moveZRelative(zStart);
			else
				StageMover.moveZAbsolute(zStart);
			core.waitForDevice(nameZ);
			if (core.isSequenceRunning()) {
				core.waitForExposure();
			}
			list.add(ImageGetter.snapImage(core));
		} catch (Exception e) {
			new AnnounceFrame("Error wile moving");
			return list;
		}
		for (int z = 1; z < slices; ++z) {
			try {
				StageMover.moveZRelative(step);
				if (core.isSequenceRunning()) {
					core.waitForExposure();
				}
				core.waitForImageSynchro();
				list.add(ImageGetter.snapImage(core));
			} catch (Exception e) {
				break;
			}
			double progress = 1D * z / slices * 90D;
			plugin.notifyProgress((int) progress);
		}
		try {
			if (absoluteZ != 0)
				core.setPosition(nameZ, absoluteZ);
		} catch (Exception e) {
			new AnnounceFrame("Error while moving");
		}
		plugin.notifyAcquisitionOver();
		return list;
	}
}