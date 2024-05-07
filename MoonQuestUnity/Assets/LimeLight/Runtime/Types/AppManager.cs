namespace PCP.LibLime
{
	public class AppManager : BasePluginBride
	{
		private void Awake()
		{
			mTag = "AppManger";
		}
		public void QuickStart()
		{
			if (!enabled)
				return;
			mPlugin.Call("QuickStart");
			enabled = false;
			LimePluginManager.Instance.StartManager(LimePluginManager.PluginType.Stream);
		}
	}
}

