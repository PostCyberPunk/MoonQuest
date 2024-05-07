namespace PCP.LibLime
{
	public class PcManager : BasePluginBride
	{
		private void Awake()
		{
			mTag = "PcManger";
		}
		public void FakeStart()
		{
			if (!enabled)
				return;
			mPlugin.Call("fakeStart");
			enabled = false;
			LimePluginManager.Instance.StartManager(LimePluginManager.PluginType.App);
		}
	}
}

